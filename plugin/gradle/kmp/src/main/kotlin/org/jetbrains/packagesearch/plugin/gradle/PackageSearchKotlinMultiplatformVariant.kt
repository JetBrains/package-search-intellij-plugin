@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import org.jetbrains.packagesearch.api.v3.ApiBaseMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiGradlePackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.GradlePackages
import org.jetbrains.packagesearch.api.v3.search.MavenPackages
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import org.jetbrains.packagesearch.plugin.core.data.UpdatePackageData
import org.jetbrains.packagesearch.plugin.core.data.WithIcon
import org.jetbrains.packagesearch.plugin.core.extensions.ProjectContext

sealed interface PackageSearchKotlinMultiplatformVariant : PackageSearchModuleVariant {

    data class SourceSet(
        override val name: String,
        override val declaredDependencies: List<PackageSearchDeclaredPackage>,
        override val badges: List<PackageSearchModuleVariant.Badge>,
        override val compatiblePackageTypes: List<PackagesType>,
        override val icon: WithIcon.PathSourceType
    ) : PackageSearchKotlinMultiplatformVariant {

        override val variantTerminology: String
            get() = "source set" // TODO localize

        override fun isCompatible(dependency: ApiPackage, version: NormalizedVersion): Boolean = when (dependency) {
            is ApiBaseMavenPackage -> MavenPackages in compatiblePackageTypes
            is ApiGradlePackage -> {
                val packageVariants = dependency.versions.all
                    .find { it.normalized == version }
                    ?.variants
                if (packageVariants != null)
                    compatiblePackageTypes.filterIsInstance<GradlePackages>()
                        .any { gradlePackageTypes ->
                            gradlePackageTypes.variants
                                .all { installedPackageVariant ->
                                    packageVariants.any { packageVariant ->
                                        installedPackageVariant.attributes.all { (_, attribute) ->
                                            packageVariant.attributes.any { (_, packageAttribute) }
                                        }
                                        TODO("WTF IT IS A MINDFUCK")
                                        true
                                    }
                                }
                        }
                false
            }
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