@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.registry.Registry
import com.intellij.packageSearch.mppDependencyUpdater.MppDependencyModifier
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Android
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Common
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Js
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Jvm
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Native
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Wasm
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoProvider
import com.jetbrains.packagesearch.plugin.core.data.IconProvider.Icons
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.utils.getIcon
import com.jetbrains.packagesearch.plugin.gradle.utils.commonConfigurations
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

class KotlinMultiplatformModuleProvider : BaseGradleModuleProvider() {

    override suspend fun FlowCollector<PackageSearchModuleData?>.transform(
        module: Module,
        context: PackageSearchModuleBuilderContext,
        model: PackageSearchGradleModel,
    ) {
        if (model.isKotlinMultiplatformApplied)
            MppCompilationInfoProvider.sourceSetsMap(context.project, model.projectDir)
                .collect { compilationModel ->
                    val pkgsModule = PackageSearchKotlinMultiplatformModule(
                        name = model.projectName,
                        identity = PackageSearchModule.Identity("gradle", model.projectIdentityPath),
                        buildFilePath = model.buildFilePath,
                        declaredKnownRepositories = context.knownRepositories - DependencyModifierService
                            .getInstance(context.project)
                            .declaredRepositories(module)
                            .mapNotNull { it.id }
                            .toSet(),
                        defaultScope = "implementation",
                        availableScopes = commonConfigurations.toList(),
                        variants = module.getKMPVariants(context = context, compilationModel = compilationModel)
                            .associateBy { it.name },
                        packageSearchModel = model,
                        availableKnownRepositories = context.knownRepositories
                    )
                    emit(
                        PackageSearchModuleData(
                            module = pkgsModule,
                            dependencyManager = PackageSearchKotlinMultiplatformDependencyManager(pkgsModule, module)
                        )
                    )
                }

    }

    suspend fun Module.getKMPVariants(
        compilationModel: Map<String, Set<MppCompilationInfoModel.Compilation>>,
        context: PackageSearchModuleBuilderContext,
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

        val isSonatype = Registry.`is`("org.jetbrains.packagesearch.sonatype")
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
                            icon = dependencyInfo[artifactModel.packageId]?.getIcon(artifactModel.version)
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
                    attributes = compilationTargets.mapNotNull {
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
                    },
                    compatiblePackageTypes = buildPackageTypes {
                        if (compilationTargets.singleOrNull() == Jvm) {
                            mavenPackages()
                        }
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
            variantName = PackageSearchKotlinMultiplatformVariant.DependenciesBlock.NAME,
            configuration = it.configuration,
            icon = it.icon
        )
    }
