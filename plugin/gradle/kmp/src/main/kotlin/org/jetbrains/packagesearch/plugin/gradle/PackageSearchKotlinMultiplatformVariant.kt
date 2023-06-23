@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiBaseMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiGradlePackage
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.GradlePackages
import org.jetbrains.packagesearch.api.v3.search.MavenPackages
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import org.jetbrains.packagesearch.plugin.core.data.WithIcon
import org.jetbrains.plugins.gradle.mpp.MppCompilationInfoModel

@Serializable
sealed interface PackageSearchKotlinMultiplatformVariant : PackageSearchModuleVariant {

    @Serializable
    data class SourceSet(
        override val name: String,
        override val declaredDependencies: List<PackageSearchKotlinMultiplatformDeclaredDependency.ForSourceSet>,
        override val badges: List<PackageSearchModuleVariant.Badge>,
        override val compatiblePackageTypes: List<PackagesType>,
        val compilerTargets: List<MppCompilationInfoModel.Compilation>
    ) : PackageSearchKotlinMultiplatformVariant {

        override val variantTerminology: String
            get() = "source set" // TODO localize

        override fun isCompatible(dependency: ApiPackage, version: String): Boolean = when (dependency) {
            is ApiBaseMavenPackage -> MavenPackages in compatiblePackageTypes
            is ApiGradlePackage -> {
                val packageVariants = dependency.versions.all[version]
                    ?.variants
                if (packageVariants != null)
                    compatiblePackageTypes.filterIsInstance<GradlePackages>()
                        .takeIf { it.isNotEmpty() }
                        ?.any { compatibleGradlePackageType ->
                            compatibleGradlePackageType.variants.all { requiredVariant ->
                                packageVariants.any { availableVariant ->
                                    requiredVariant.attributes.all { (attributeName, attribute) ->
                                        availableVariant.attributes[attributeName]
                                            ?.let { attribute.isCompatible(it) } ?: false
                                    }
                                }
                            }
                        } ?: false
                else false
            }
        }
    }

    @Serializable
    data class DependenciesBlock(
        override val declaredDependencies: List<PackageSearchKotlinMultiplatformDeclaredDependency.Maven>,
        override val badges: List<PackageSearchModuleVariant.Badge>,
        override val compatiblePackageTypes: List<PackagesType>,
    ) : PackageSearchKotlinMultiplatformVariant {
        override val name: String = "dependencies block" // TODO localize
        override val variantTerminology = null

        override fun isCompatible(dependency: ApiPackage, version: String) = when (dependency) {
            is ApiMavenPackage -> true
            else -> false
        }
    }

    @Serializable
    data class Cocoapods(
        override val declaredDependencies: List<PackageSearchKotlinMultiplatformDeclaredDependency.Cocoapods>,
        override val badges: List<PackageSearchModuleVariant.Badge>,
        override val compatiblePackageTypes: List<PackagesType>,
    ) : PackageSearchKotlinMultiplatformVariant {

        override val name: String = "cocoapods"
        override val variantTerminology = null

        override fun isCompatible(dependency: ApiPackage, version: String) = when(dependency) {
            is ApiBaseMavenPackage, is ApiGradlePackage -> false
        }
    }
}