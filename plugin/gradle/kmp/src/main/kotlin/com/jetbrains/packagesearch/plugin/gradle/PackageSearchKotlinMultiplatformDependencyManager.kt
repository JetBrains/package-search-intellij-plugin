package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.module.Module
import com.intellij.packageSearch.mppDependencyUpdater.MppDependency
import com.intellij.packageSearch.mppDependencyUpdater.MppDependencyModifier
import com.intellij.packageSearch.mppDependencyUpdater.MppModifierUpdateData
import com.jetbrains.packagesearch.plugin.core.data.InstallPackageData
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.RemovePackageData
import com.jetbrains.packagesearch.plugin.core.data.UpdatePackageData
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchKnownRepositoriesContext
import com.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

class PackageSearchKotlinMultiplatformDependencyManager(
    model: PackageSearchGradleModel,
    private val module: PackageSearchKotlinMultiplatformModule,
    private val nativeModule: Module,
) : PackageSearchDependencyManager {

    private val baseDependencyManager = PackageSearchGradleDependencyManager(model, nativeModule)

    override suspend fun updateDependencies(
        context: PackageSearchKnownRepositoriesContext,
        data: List<UpdatePackageData>,
    ) {
        val dependencyBlockUpdates = mutableListOf<GradleUpdatePackageData>()
        val sourceSetsUpdates = mutableListOf<MppModifierUpdateData>()
        data.filterIsInstance<KotlinMultiplatformUpdatePackageData>()
            .filter { it.sourceSetName in module.variants }
            .filter { it.newVersion != null || it.newScope != null }
            .forEach {
                val variant = module.variants.getValue(it.sourceSetName)
                when (variant) {
                    is PackageSearchKotlinMultiplatformVariant.Cocoapods -> TODO()
                    is PackageSearchKotlinMultiplatformVariant.DependenciesBlock -> it.handleDependenciesBlockUpdate(dependencyBlockUpdates)
                    is PackageSearchKotlinMultiplatformVariant.SourceSet -> when (it.installedPackage) {
                        is PackageSearchKotlinMultiplatformDeclaredDependency.Maven -> it.handleSourceSetUpdate(sourceSetsUpdates)
                        is PackageSearchKotlinMultiplatformDeclaredDependency.Cocoapods -> TODO()
                        is PackageSearchKotlinMultiplatformDeclaredDependency.Npm -> TODO()
                    }
                }
            }
        baseDependencyManager.updateGradleDependencies(context, dependencyBlockUpdates)
        MppDependencyModifier.updateDependencies(nativeModule, sourceSetsUpdates)
    }

    private fun KotlinMultiplatformUpdatePackageData.handleDependenciesBlockUpdate(
        dependencyBlockUpdates: MutableList<GradleUpdatePackageData>
    ) = when (installedPackage) {
        is PackageSearchKotlinMultiplatformDeclaredDependency.Maven -> {
            dependencyBlockUpdates.add(
                GradleUpdatePackageData(
                    installedPackage = installedPackage,
                    newVersion = newVersion,
                    newScope = newScope ?: installedPackage.configuration
                )
            )
        }
        is PackageSearchKotlinMultiplatformDeclaredDependency.Cocoapods ->
            error("Cocoapods dependencies cannot be declared in dependencies block")
        is PackageSearchKotlinMultiplatformDeclaredDependency.Npm ->
            error("Npm dependencies cannot be declared in dependencies block")
    }

    private fun KotlinMultiplatformUpdatePackageData.handleSourceSetUpdate(
        sourceSetsUpdates: MutableList<MppModifierUpdateData>
    ) {
        when (installedPackage) {
            is PackageSearchKotlinMultiplatformDeclaredDependency.Maven -> {
                sourceSetsUpdates.add(
                    MppModifierUpdateData(
                        sourceSet = sourceSetName,
                        oldDescriptor = MppDependency.Maven(
                            groupId = installedPackage.groupId,
                            artifactId = installedPackage.artifactId,
                            version = installedPackage.declaredVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName,
                            configuration = installedPackage.configuration
                        ),
                        newDescriptor = MppDependency.Maven(
                            groupId = installedPackage.groupId,
                            artifactId = installedPackage.artifactId,
                            version = newVersion
                                ?: installedPackage.declaredVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName,
                            configuration = newScope ?: installedPackage.configuration
                        )
                    )
                )
            }

            is PackageSearchKotlinMultiplatformDeclaredDependency.Npm -> TODO("One day!")
            is PackageSearchKotlinMultiplatformDeclaredDependency.Cocoapods ->
                error("Cocoapods dependencies cannot be declared in source sets")
        }
    }


    override suspend fun addDependency(context: PackageSearchKnownRepositoriesContext, data: InstallPackageData) {
        val kmpData = data as? KotlinMultiplatformInstallPackageData ?: return
        val variant = module.variants[kmpData.variantName] ?: return
        if (!variant.isCompatible(kmpData.apiPackage, kmpData.selectedVersion)) return
        when (kmpData.apiPackage) {
            is ApiMavenPackage -> when (variant) {
                is PackageSearchKotlinMultiplatformVariant.Cocoapods -> TODO()
                is PackageSearchKotlinMultiplatformVariant.DependenciesBlock -> baseDependencyManager.installGradleDependencies(
                    context = context,
                    data = GradleInstallPackageData(
                        apiPackage = kmpData.apiPackage,
                        selectedVersion = kmpData.selectedVersion,
                        selectedConfiguration = kmpData.selectedConfiguration
                    )
                )
                is PackageSearchKotlinMultiplatformVariant.SourceSet -> MppDependencyModifier.addDependency(
                    module = nativeModule,
                    sourceSet = kmpData.variantName,
                    descriptor = MppDependency.Maven(
                        groupId = kmpData.apiPackage.groupId,
                        artifactId = kmpData.apiPackage.artifactId,
                        version = kmpData.selectedVersion,
                        configuration = kmpData.selectedConfiguration
                    )
                )
            }
        }
    }

    override suspend fun removeDependency(context: ProjectContext, data: RemovePackageData) {
        val kmpData = data as? KotlinMultiplatformRemovePackageData ?: return
        val variant = module.variants[kmpData.variantName] ?: return
        when (variant) {
            is PackageSearchKotlinMultiplatformVariant.Cocoapods -> TODO()
            is PackageSearchKotlinMultiplatformVariant.DependenciesBlock -> when (kmpData.declaredPackage) {
                is PackageSearchKotlinMultiplatformDeclaredDependency.Maven -> baseDependencyManager.removeGradleDependencies(
                    context = context,
                    data = GradleRemovePackageData(kmpData.declaredPackage)
                )
                else -> return
            }
            is PackageSearchKotlinMultiplatformVariant.SourceSet -> when (kmpData.declaredPackage) {
                is PackageSearchKotlinMultiplatformDeclaredDependency.Cocoapods -> return
                is PackageSearchKotlinMultiplatformDeclaredDependency.Maven -> MppDependencyModifier.removeDependency(
                    module = nativeModule,
                    sourceSet = kmpData.variantName,
                    descriptor = MppDependency.Maven(
                        groupId = kmpData.declaredPackage.groupId,
                        artifactId = kmpData.declaredPackage.artifactId,
                        version = kmpData.declaredPackage.declaredVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName,
                        configuration = kmpData.declaredPackage.configuration,
                    )
                )
                is PackageSearchKotlinMultiplatformDeclaredDependency.Npm -> TODO()
            }
        }
    }
}