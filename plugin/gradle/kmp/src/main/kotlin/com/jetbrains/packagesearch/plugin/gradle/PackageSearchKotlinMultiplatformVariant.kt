@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiPackageVersion
import org.jetbrains.packagesearch.api.v3.search.PackagesType

@Serializable
sealed interface PackageSearchKotlinMultiplatformVariant : PackageSearchModuleVariant {

    override val dependencyMustHaveAScope: Boolean
        get() = true

    @Serializable
    data class SourceSet(
        override val name: String,
        override val declaredDependencies: List<PackageSearchKotlinMultiplatformDeclaredDependency>,
        override val attributes: List<PackageSearchModuleVariant.Attribute>,
        override val compatiblePackageTypes: List<PackagesType>,
        val compilerTargets: Set<MppCompilationInfoModel.Compilation>,
    ) : PackageSearchKotlinMultiplatformVariant {

        companion object {
            val TERMINOLOGY = PackageSearchModuleVariant.Terminology("source set", "source sets")
        }

        override val isPrimary: Boolean
            get() = name.contains("main", ignoreCase = true)

        override val variantTerminology: PackageSearchModuleVariant.Terminology
            get() = TERMINOLOGY

        override fun isCompatible(dependency: ApiPackage, version: ApiPackageVersion): Boolean = when (dependency) {
            is ApiMavenPackage -> when (version) {
                is ApiMavenPackage.MavenVersion -> PackagesType.Maven in compatiblePackageTypes
                is ApiMavenPackage.GradleVersion -> compatiblePackageTypes.filterIsInstance<PackagesType.Gradle>()
                    .takeIf { it.isNotEmpty() }
                    ?.any { compatibleGradlePackageType ->
                        compatibleGradlePackageType.variants.all { requiredVariant ->
                            version.variants.any { availableVariant ->
                                requiredVariant.attributes.all { (attributeName, attribute) ->
                                    availableVariant.attributes[attributeName]
                                        ?.let { attribute.isCompatible(it) } ?: false
                                }
                            }
                        }
                    } ?: false
            }
        }

        override fun getInstallData(
            apiPackage: ApiPackage,
            selectedVersion: ApiPackageVersion,
            selectedScope: String?,
        ): KotlinMultiplatformInstallPackageData = when (apiPackage) {
            is ApiMavenPackage -> KotlinMultiplatformInstallPackageData(
                apiPackage = apiPackage,
                selectedVersion = selectedVersion,
                selectedConfiguration = selectedScope ?: "implementation",
                variantName = name
            )
        }
    }

    @Serializable
    data class DependenciesBlock(
        override val declaredDependencies: List<PackageSearchKotlinMultiplatformDeclaredDependency.Maven>,
        override val compatiblePackageTypes: List<PackagesType>,
    ) : PackageSearchKotlinMultiplatformVariant {

        override val attributes: List<PackageSearchModuleVariant.Attribute>
            get() = emptyList()

        companion object {
            const val NAME = "dependencies block"
        }

        override val name: String = NAME
        override val variantTerminology = null

        override val isPrimary: Boolean
            get() = false

        override fun isCompatible(dependency: ApiPackage, version: ApiPackageVersion) = when (dependency) {
            is ApiMavenPackage -> true
            else -> false
        }

        override fun getInstallData(
            apiPackage: ApiPackage,
            selectedVersion: ApiPackageVersion,
            selectedScope: String?,
        ) = when (apiPackage) {
            is ApiMavenPackage -> KotlinMultiplatformInstallPackageData(
                apiPackage = apiPackage,
                selectedVersion = selectedVersion,
                selectedConfiguration = selectedScope ?: "implementation",
                variantName = name
            )
        }
    }

    @Serializable
    data class Cocoapods(
        override val declaredDependencies: List<PackageSearchKotlinMultiplatformDeclaredDependency.Cocoapods>,
        override val compatiblePackageTypes: List<PackagesType>,
    ) : PackageSearchKotlinMultiplatformVariant {

        override val isPrimary: Boolean
            get() = false

        override val attributes: List<PackageSearchModuleVariant.Attribute>
            get() = listOf(PackageSearchModuleVariant.Attribute.StringAttribute(NAME))

        companion object {
            const val NAME = "cocoapods"
        }

        override val name: String = NAME
        override val variantTerminology = null

        override fun isCompatible(dependency: ApiPackage, version: ApiPackageVersion) = when (dependency) {
            is ApiMavenPackage -> false
        }

        override fun getInstallData(
            apiPackage: ApiPackage,
            selectedVersion: ApiPackageVersion,
            selectedScope: String?,
        ) = when (apiPackage) {
            is ApiMavenPackage -> error("Cannot install a Maven package as a Cocoapods dependency")
        }
    }
}