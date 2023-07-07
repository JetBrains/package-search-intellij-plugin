@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.registry.Registry
import com.intellij.packageSearch.mppDependencyUpdater.MppDependencyModifier
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoProvider
import io.ktor.util.extension
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.kotlinMultiplatform
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import org.jetbrains.packagesearch.plugin.gradle.utils.dependencyDeclarationIndexes

class KotlinMultiplatformModuleTransformer : BaseGradleModuleTransformer() {

    override suspend fun Module.transform(
        context: PackageSearchModuleBuilderContext,
        model: PackageSearchGradleModel,
        buildFile: Path?,
    ): PackageSearchModuleData {
        val isKts = buildFile?.extension == "kts"
        val dependencyBlockDeclaredDependencies =
            getDeclaredDependencies(context, isKts)
                .asKmpVariantDependencies()
        val module = PackageSearchKotlinMultiplatformModule(
            name = model.projectName,
            identity = PackageSearchModule.Identity("gradle", model.projectIdentityPath),
            buildFilePath = buildFile?.absolutePathString(),
            declaredKnownRepositories = context.knownRepositories - DependencyModifierService
                .getInstance(context.project)
                .declaredRepositories(this)
                .mapNotNull { it.id }
                .toSet(),
            defaultScope = "implementation",
            availableScopes = commonConfigurations.toList(),
            variants = getKMPVariants(context, dependencyBlockDeclaredDependencies, isKts).associateBy { it.name },
            packageSearchModel = model,
            availableKnownRepositories = context.knownRepositories
        )
        return PackageSearchModuleData(
            module = module,
            dependencyManager = PackageSearchKotlinMultiplatformDependencyManager(module, this)
        )
    }

    suspend fun Module.getKMPVariants(
        context: PackageSearchModuleBuilderContext,
        dependencyBlockDeclaredDependencies: List<PackageSearchKotlinMultiplatformDeclaredDependency.Maven>,
        isKts: Boolean
    ): List<PackageSearchKotlinMultiplatformVariant> = coroutineScope {
        val dependenciesBlockVariant = async {
            PackageSearchKotlinMultiplatformVariant.DependenciesBlock(
                dependencyBlockDeclaredDependencies,
                emptyList(),
                compatiblePackageTypes = buildPackageTypes {
                    mavenPackages()
                    gradlePackages {
                        mustBeRootPublication = true
                    }
                }
            )
        }
        val rawDeclaredSourceSetDependencies = MppDependencyModifier
            .dependenciesBySourceSet(this@getKMPVariants)
            ?.mapNotNull { (key, value) -> value?.let { key to value } }
            ?.toMap()
            ?.mapValues { readAction { it.value.artifacts().map { it to it.spec } } }
            ?: emptyMap()

        val packageIds = rawDeclaredSourceSetDependencies
            .values
            .asSequence()
            .flatten()
            .mapNotNull { it.second.mavenId }
            .distinct()

        val isLocalhost = Registry.`is`("org.jetbrains.packagesearch.localhost", false)
        val dependencyInfo = if (!isLocalhost) {
            context.getPackageInfoByIdHashes(packageIds.map { ApiPackage.hashPackageId(it) }.toSet())
        } else {
            context.getPackageInfoByIds(packageIds.toSet())
        }
        val declaredSourceSetDependencies = rawDeclaredSourceSetDependencies
            .mapValues { (sourceSetName, dependencies) ->
                dependencies.mapNotNull { (model, spec) ->
                    val mavenId = spec.mavenId ?: return@mapNotNull null
                    val groupId = spec.group ?: return@mapNotNull null
                    val configuration = spec.classifier ?: return@mapNotNull null
                    PackageSearchKotlinMultiplatformDeclaredDependency.Maven(
                        id = mavenId,
                        declaredVersion = NormalizedVersion.from(spec.version),
                        latestStableVersion = dependencyInfo[mavenId]?.versions?.latestStable?.normalized
                            ?: NormalizedVersion.Missing,
                        latestVersion = dependencyInfo[mavenId]?.versions?.latest?.normalized
                            ?: NormalizedVersion.Missing,
                        remoteInfo = dependencyInfo[mavenId] as? ApiMavenPackage,
                        declarationIndexes = dependencyDeclarationIndexes(
                            groupId = groupId,
                            artifactId = spec.name,
                            version = spec.version,
                            isKts = isKts,
                            configuration = configuration,
                            psiElement = model.psiElement
                        ),
                        groupId = groupId,
                        artifactId = spec.name,
                        configuration = configuration,
                        variantName = sourceSetName
                    )
                }

            }
        val sourceSetVariants =
            MppCompilationInfoProvider.sourceSetsMap(this@getKMPVariants)
                ?.mapKeys { it.key.name }
                ?.map { (sourceSetName, compilationTargets) ->
                    PackageSearchKotlinMultiplatformVariant.SourceSet(
                        name = sourceSetName,
                        declaredDependencies = declaredSourceSetDependencies[sourceSetName] ?: emptyList(),
                        attributes = emptyList(), // TODO
                        compatiblePackageTypes = buildPackageTypes {
                            gradlePackages {
                                kotlinMultiplatform {
                                    compilationTargets.forEach { compilationTarget ->
                                        when (compilationTarget) {
                                            is MppCompilationInfoModel.Js -> when (compilationTarget.compiler) {
                                                MppCompilationInfoModel.Js.Compiler.IR -> jsIr()
                                                MppCompilationInfoModel.Js.Compiler.LEGACY -> jsLegacy()
                                            }

                                            is MppCompilationInfoModel.Native -> native(compilationTarget.platformId)
                                            else -> {}
                                        }
                                    }
                                    when {
                                        MppCompilationInfoModel.Android in compilationTargets -> android()
                                        MppCompilationInfoModel.Jvm in compilationTargets -> jvm()
                                    }
                                }
                            }
                        },
                        compilerTargets = compilationTargets
                    )
                }
                ?: emptyList()

        sourceSetVariants + dependenciesBlockVariant.await()
    }
}

fun List<PackageSearchGradleDeclaredPackage>.asKmpVariantDependencies() =
    map {
        PackageSearchKotlinMultiplatformDeclaredDependency.Maven(
            id = it.id,
            declaredVersion = it.declaredVersion,
            latestStableVersion = it.latestStableVersion,
            latestVersion = it.latestVersion,
            remoteInfo = it.remoteInfo,
            declarationIndexes = it.declarationIndexes,
            groupId = it.groupId,
            artifactId = it.artifactId,
            configuration = it.configuration,
            variantName = "dependencies block"
        )
    }

val ArtifactDependencySpec.mavenId: String?
    get() {
        val group = group ?: return null
        val artifactId = name
        return "maven:$group$artifactId"
    }