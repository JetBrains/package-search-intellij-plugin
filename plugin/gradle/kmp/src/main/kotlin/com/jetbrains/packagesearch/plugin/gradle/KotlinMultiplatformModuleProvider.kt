@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
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
import com.jetbrains.packagesearch.plugin.core.utils.icon
import com.jetbrains.packagesearch.plugin.gradle.utils.getDeclaredDependencies
import com.jetbrains.packagesearch.plugin.gradle.utils.toGradleDependencyModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.androidPackages
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.jvmGradlePackages
import org.jetbrains.packagesearch.api.v3.search.kotlinMultiplatform
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

class KotlinMultiplatformModuleProvider : AbstractGradleModuleProvider() {

    context(PackageSearchModuleBuilderContext)
    override suspend fun FlowCollector<PackageSearchModule?>.transform(
        module: Module,
        model: PackageSearchGradleModel,
    ) {
        if (model.isKotlinMultiplatformApplied)
            MppCompilationInfoProvider.sourceSetsMap(project, model.projectDir)
                .collect { compilationModel ->
                    val variants = module.getKMPVariants(
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
                        declaredKnownRepositories = knownRepositories - DependencyModifierService
                            .getInstance(project)
                            .declaredRepositories(module)
                            .mapNotNull { it.id }
                            .toSet(),
                        variants = variants,
                        packageSearchModel = model,
                        availableKnownRepositories = knownRepositories,
                        nativeModule = module
                    )
                    emit(pkgsModule)
                }

    }

    context(PackageSearchModuleBuilderContext)
    suspend fun Module.getKMPVariants(
        compilationModel: Map<String, Set<MppCompilationInfoModel.Compilation>>,
        availableScopes: List<String>,
    ): List<PackageSearchKotlinMultiplatformVariant> = coroutineScope {
        val dependenciesBlockVariant = async {
            val declaredDependencies = getDeclaredDependencies()
            PackageSearchKotlinMultiplatformVariant.DependenciesBlock(
                declaredDependencies = declaredDependencies.asKmpVariantDependencies(),
                compatiblePackageTypes = buildPackageTypes {
                    mavenPackages()
                    gradlePackages {
                        isRootPublication = true
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
            ?.mapValues { readAction { it.value.artifacts().map { it.toGradleDependencyModel() } }.distinct() }
            ?: emptyMap()

        val packageIds = rawDeclaredSourceSetDependencies
            .values
            .asSequence()
            .flatten()
            .distinct()
            .map { it.packageId }

        val dependencyInfo = getPackageInfoByIdHashes(packageIds.map { ApiPackage.hashPackageId(it) }.toSet())

        val declaredSourceSetDependencies =
            rawDeclaredSourceSetDependencies
                .mapValues { (sourceSetName, dependencies) ->
                    dependencies.map { artifactModel ->
                        PackageSearchKotlinMultiplatformDeclaredDependency.Maven(
                            id = artifactModel.packageId,
                            declaredVersion = artifactModel.version?.let { NormalizedVersion.fromStringOrNull(it) },
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
                        gradlePackages {
                            kotlinMultiplatform {
                                compilationTargets.forEach { compilationTarget ->
                                    when  {
                                        compilationTarget is Js -> when (compilationTarget.compiler) {
                                            Js.Compiler.IR -> jsIr()
                                            Js.Compiler.LEGACY -> jsLegacy()
                                        }
                                        compilationTarget is Native -> native(compilationTarget.target)
                                        compilationTarget == MppCompilationInfoModel.Wasm -> wasm()
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

