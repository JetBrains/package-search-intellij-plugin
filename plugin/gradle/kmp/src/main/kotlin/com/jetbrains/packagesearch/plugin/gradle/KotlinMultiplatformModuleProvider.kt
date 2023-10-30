@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.registry.Registry
import com.intellij.packageSearch.mppDependencyUpdater.MppDependencyModifier
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Android
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Js
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Jvm
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Native
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoProvider
import com.jetbrains.packagesearch.plugin.core.data.IconProvider.Icons
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.utils.icon
import com.jetbrains.packagesearch.plugin.gradle.utils.getDeclaredDependencies
import com.jetbrains.packagesearch.plugin.gradle.utils.toGradleDependencyModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.kotlinMultiplatform
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

class KotlinMultiplatformModuleProvider : AbstractGradleModuleProvider() {

    override suspend fun FlowCollector<PackageSearchModuleData?>.transform(
        module: Module,
        context: PackageSearchModuleBuilderContext,
        model: PackageSearchGradleModel,
    ) {
        if (model.isKotlinMultiplatformApplied)
            MppCompilationInfoProvider.sourceSetsMap(context.project, model.projectDir)
                .collect { compilationModel ->
                    val variants = module.getKMPVariants(
                        context = context,
                        compilationModel = compilationModel,
                        availableScopes = model.configurations
                            .filter { it.canBeDeclared }
                            .map { it.name }
                    ).associateBy { it.name }
                    val pkgsModule = PackageSearchKotlinMultiplatformModule(
                        name = model.projectName,
                        identity = PackageSearchModule.Identity(
                            group = "gradle",
                            path = model.projectIdentityPath
                        ),
                        buildFilePath = model.buildFilePath,
                        declaredKnownRepositories = context.knownRepositories - DependencyModifierService
                            .getInstance(context.project)
                            .declaredRepositories(module)
                            .mapNotNull { it.id }
                            .toSet(),
                        variants = variants,
                        packageSearchModel = model,
                        availableKnownRepositories = context.knownRepositories
                    )
                    emit(
                        PackageSearchModuleData(
                            module = pkgsModule,
                            dependencyManager = PackageSearchKotlinMultiplatformDependencyManager(
                                model,
                                pkgsModule,
                                module
                            )
                        )
                    )
                }

    }

    suspend fun Module.getKMPVariants(
        compilationModel: Map<String, Set<MppCompilationInfoModel.Compilation>>,
        context: PackageSearchModuleBuilderContext,
        availableScopes: List<String>,
    ): List<PackageSearchKotlinMultiplatformVariant> = coroutineScope {
        val dependenciesBlockVariant = async {
            val declaredDependencies = getDeclaredDependencies(context)
            PackageSearchKotlinMultiplatformVariant.DependenciesBlock(
                declaredDependencies = declaredDependencies.asKmpVariantDependencies(),
                compatiblePackageTypes = buildPackageTypes {
                    mavenPackages()
                    gradlePackages {
                        mustBeRootPublication = true
                    }
                },
                availableScopes = availableScopes,
                defaultScope = "implementation".takeIf { it in availableScopes }
                    ?: declaredDependencies.map { it.configuration }
                        .groupBy { it }
                        .mapValues { it.value.count() }
                        .entries
                        .maxByOrNull { it.value }
                        ?.key
                    ?: availableScopes.first()
            )
        }

        val rawDeclaredSourceSetDependencies = MppDependencyModifier
            .dependenciesBySourceSet(this@getKMPVariants)
            ?.filterNotNullValues()
            ?.mapValues { readAction { it.value.artifacts().map { it.toGradleDependencyModel() } } }
            ?: emptyMap()

        val packageIds = rawDeclaredSourceSetDependencies
            .values
            .asSequence()
            .flatten()
            .distinct()
            .map { it.packageId }

        val isSonatype = Registry.`is`("packagesearch.sonatype.api.client")
        val dependencyInfo = if (!isSonatype) {
            context.getPackageInfoByIdHashes(packageIds.map { ApiPackage.hashPackageId(it) }.toSet())
        } else {
            context.getPackageInfoByIds(packageIds.toSet())
        }

        val declaredSourceSetDependencies =
            rawDeclaredSourceSetDependencies
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
                            variantName = sourceSetName,
                            configuration = artifactModel.configuration,
                            icon = dependencyInfo[artifactModel.packageId]?.icon
                                ?: Icons.GRADLE
                        )
                    }
                }
        val sourceSetVariants = compilationModel
            .mapKeys { it.key }
            .map { (sourceSetName, compilationTargets) ->
                PackageSearchKotlinMultiplatformVariant.SourceSet(
                    name = sourceSetName,
                    declaredDependencies = declaredSourceSetDependencies[sourceSetName] ?: emptyList(),
                    attributes = compilationTargets.buildAttributes(),
                    compatiblePackageTypes = buildPackageTypes {
                        if (compilationTargets.singleOrNull() == Jvm) {
                            mavenPackages()
                        } else gradlePackages {
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

        sourceSetVariants + dependenciesBlockVariant.await()
    }
}

