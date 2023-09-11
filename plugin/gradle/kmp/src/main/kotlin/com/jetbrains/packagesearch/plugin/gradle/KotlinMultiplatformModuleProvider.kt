@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.registry.Registry
import com.intellij.packageSearch.mppDependencyUpdater.MppDependencyModifier
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Android
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Common
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Js
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Jvm
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Native
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Wasm
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoProvider
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppDataNodeProcessor
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.gradle.utils.toGradleDependencyModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.kotlinMultiplatform
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class KotlinMultiplatformModuleProvider : BaseGradleModuleProvider() {

    override suspend fun Module.transform(
        context: PackageSearchModuleBuilderContext,
        model: PackageSearchGradleModel,
        buildFile: Path?,
    ): PackageSearchModuleData? {
        if (!model.isKotlinMultiplatformApplied) return null

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
            variants = getKMPVariants(context = context, projectDir = model.projectDir)
                .associateBy { it.name }
                .takeIf { it.isNotEmpty() }
                ?: return null,
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
        projectDir: String,
    ): List<PackageSearchKotlinMultiplatformVariant> = coroutineScope {
        val dependenciesBlockVariant = async {
            PackageSearchKotlinMultiplatformVariant.DependenciesBlock(
                declaredDependencies = getDeclaredDependencies(context).asKmpVariantDependencies(),
                compatiblePackageTypes = buildPackageTypes {
                    mavenPackages()
                    gradlePackages {
                        mustBeRootPublication = true
                    }
                }
            )
        }
        val sourceSetPlatformMap = context.project.service<MppDataNodeProcessor.Cache>()
            .state[projectDir]
            ?.compilationsBySourceSet
            ?.entries
            ?.associateBy(
                keySelector = { it.key.name },
                valueTransform = {
                    it.value.mapNotNull {
                        when (it) {
                            Android, Jvm, Wasm -> it.platformId
                            Common -> null
                            is Js -> when (it.compiler) {
                                Js.Compiler.IR -> "jsIr"
                                Js.Compiler.LEGACY -> "jsLegacy"
                            }
                            is Native -> when (it.target) {
                                else -> it.target
                            }
                        }
                    }
                }
            )
            ?: emptyMap()


        val rawDeclaredSourceSetDependencies = MppDependencyModifier
            .dependenciesBySourceSet(this@getKMPVariants)
            ?.mapNotNull { (key, value) ->
                value?.let {
                    key to readAction {
                        it.artifacts().map {
                            it.toGradleDependencyModel()
                        }
                    }
                }
            }
            ?.toMap()
            ?: emptyMap()

        val packageIds = rawDeclaredSourceSetDependencies
            .values
            .asSequence()
            .flatten()
            .distinct()
            .map { it.packageId }

        val isLocalhost = Registry.`is`("org.jetbrains.packagesearch.localhost", false)
        val dependencyInfo = if (!isLocalhost) {
            context.getPackageInfoByIdHashes(packageIds.map { ApiPackage.hashPackageId(it) }.toSet())
        } else {
            context.getPackageInfoByIds(packageIds.toSet())
        }
        val declaredSourceSetDependencies = rawDeclaredSourceSetDependencies
            .mapValues { (sourceSetName, dependencies) ->
                dependencies.map { artifactModel ->
                    PackageSearchKotlinMultiplatformDeclaredDependency.Maven(
                        id = artifactModel.packageId,
                        declaredVersion = NormalizedVersion.from(artifactModel.version),
                        latestStableVersion = dependencyInfo[artifactModel.packageId]?.versions?.latestStable?.normalized
                            ?: NormalizedVersion.Missing,
                        latestVersion = dependencyInfo[artifactModel.packageId]?.versions?.latest?.normalized
                            ?: NormalizedVersion.Missing,
                        remoteInfo = dependencyInfo[artifactModel.packageId] as? ApiMavenPackage,
                        declarationIndexes = artifactModel.indexes,
                        groupId = artifactModel.groupId,
                        artifactId = artifactModel.artifactId,
                        configuration = artifactModel.configuration,
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
                        attributes = sourceSetPlatformMap[sourceSetName] ?: emptyList(),
                        compatiblePackageTypes = buildPackageTypes {
                            gradlePackages {
                                kotlinMultiplatform {
                                    compilationTargets.forEach { compilationTarget ->
                                        when (compilationTarget) {
                                            is Js -> when (compilationTarget.compiler) {
                                                Js.Compiler.IR -> jsIr()
                                                Js.Compiler.LEGACY -> jsLegacy()
                                            }

                                            is Native -> native(compilationTarget.target)
                                            else -> {}
                                        }
                                    }
                                    when {
                                        Android in compilationTargets -> android()
                                        Jvm in compilationTargets -> jvm()
                                    }
                                }
                            }
                        },
                        compilerTargets = compilationTargets
                    )
                }
                ?: return@coroutineScope emptyList()

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
