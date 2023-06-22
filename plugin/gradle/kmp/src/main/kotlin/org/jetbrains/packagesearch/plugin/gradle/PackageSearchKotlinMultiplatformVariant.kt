@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.project.modules
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import org.jetbrains.packagesearch.plugin.core.data.UpdatePackageData
import org.jetbrains.packagesearch.plugin.core.data.WithIcon
import org.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import org.jetbrains.plugins.gradle.mpp.MppDependency
import org.jetbrains.plugins.gradle.mpp.MppDependencyModificator

fun PackageSearchKotlinMultiplatformVariant.getNativeModule(context: ProjectContext) =
    context.project.modules.find { it.moduleFile?.path == modulePath }
        ?: error(
            "Could not find native module for Variant '$name'" +
                    " of type ${this::class.simpleName}"
        )


sealed interface PackageSearchKotlinMultiplatformVariant : PackageSearchModuleVariant {

    val modulePath: String

    data class SourceSet(
        override val name: String,
        override val declaredDependencies: List<PackageSearchDeclaredPackage>,
        override val badges: List<PackageSearchModuleVariant.Badge>,
        override val compatiblePackageTypes: List<PackagesType>,
        override val icon: WithIcon.PathSourceType,
        override val modulePath: String
    ) : PackageSearchKotlinMultiplatformVariant {

        override val variantTerminology: String
            get() = "source set" // TODO localize

        override suspend fun updateDependencies(
            context: ProjectContext,
            updateCandidates: List<UpdatePackageData>,
            knownRepositories: List<ApiRepository>
        ) {
            val modifier = MppDependencyModificator.getInstance(context.project)
            val mppUpdates = updateCandidates.filterIsInstance<GradleUpdatePackageData>()
                .map { (installedPackage, version, configuration) ->
                    val from = MppDependency(
                        unifiedDependency = UnifiedDependency(
                            groupId = installedPackage.module,
                            artifactId = installedPackage.name,
                            version = installedPackage.declaredVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName,
                            configuration = installedPackage.configuration
                        ),
                        sourceSet = name
                    )
                    val to = MppDependency(
                        unifiedDependency = UnifiedDependency(
                            groupId = installedPackage.module,
                            artifactId = installedPackage.name,
                            version = version
                                ?: installedPackage.declaredVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName,
                            configuration = configuration
                        ),
                        sourceSet = name
                    )
                    from to to
                }
            modifier.updateDependencies(getNativeModule(context), mppUpdates)
        }

        override suspend fun installDependency(
            context: ProjectContext,
            apiPackage: ApiPackage,
            selectedVersion: String,
            selectedScope: String?
        ) {
            TODO("Not yet implemented")
        }

        override suspend fun removeDependency(context: ProjectContext, installedPackage: PackageSearchDeclaredPackage) {
            TODO("Not yet implemented")
        }

        override fun isCompatible(dependency: ApiPackage): Boolean {
            TODO("Not yet implemented")
        }
    }

    data class DependenciesBlock(
        override val declaredDependencies: List<PackageSearchDeclaredPackage>,
        override val badges: List<PackageSearchModuleVariant.Badge>,
        override val compatiblePackageTypes: List<PackagesType>,
        override val icon: WithIcon.PathSourceType
    ) : PackageSearchKotlinMultiplatformVariant {
        override val name: String = "dependencies block" // TODO localize
        override val variantTerminology = null

        override suspend fun updateDependencies(
            context: ProjectContext,
            updateCandidates: List<UpdatePackageData>,
            knownRepositories: List<ApiRepository>
        ) {
            TODO("Not yet implemented")
        }

        override suspend fun installDependency(
            context: ProjectContext,
            apiPackage: ApiPackage,
            selectedVersion: String,
            selectedScope: String?
        ) {
            TODO("Not yet implemented")
        }

        override suspend fun removeDependency(context: ProjectContext, installedPackage: PackageSearchDeclaredPackage) {
            TODO("Not yet implemented")
        }

        override fun isCompatible(dependency: ApiPackage): Boolean {
            TODO("Not yet implemented")
        }
    }

    data class Cocoapods(
        override val declaredDependencies: List<PackageSearchDeclaredPackage>,
        override val badges: List<PackageSearchModuleVariant.Badge>,
        override val compatiblePackageTypes: List<PackagesType>,
        override val icon: WithIcon.PathSourceType
    ) : PackageSearchKotlinMultiplatformVariant {
        override val name: String = "cocoapods"
        override val variantTerminology = null
        override suspend fun updateDependencies(
            context: ProjectContext,
            updateCandidates: List<UpdatePackageData>,
            knownRepositories: List<ApiRepository>
        ) {
            TODO("Not yet implemented")
        }

        override suspend fun installDependency(
            context: ProjectContext,
            apiPackage: ApiPackage,
            selectedVersion: String,
            selectedScope: String?
        ) {
            TODO("Not yet implemented")
        }

        override suspend fun removeDependency(context: ProjectContext, installedPackage: PackageSearchDeclaredPackage) {
            TODO("Not yet implemented")
        }

        override fun isCompatible(dependency: ApiPackage): Boolean {
            TODO("Not yet implemented")
        }
    }
}