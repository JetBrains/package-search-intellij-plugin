@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiMavenVersion
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant

@Serializable
sealed interface PackageSearchKotlinMultiplatformVariant : PackageSearchModuleVariant {

    @Serializable
    data class SourceSet(
        override val name: String,
        override val declaredDependencies: List<PackageSearchKotlinMultiplatformDeclaredDependency>,
        override val attributes: List<String>,
        override val compatiblePackageTypes: List<PackagesType>,
        val compilerTargets: Set<MppCompilationInfoModel.Compilation>
    ) : PackageSearchKotlinMultiplatformVariant {

        companion object {
            val TERMINOLOGY = PackageSearchModuleVariant.Terminology("source set", "source sets")
        }

        override val isPrimary: Boolean
            get() = name.contains("main", ignoreCase = true)

        override val variantTerminology: PackageSearchModuleVariant.Terminology
            get() = TERMINOLOGY

        override fun isCompatible(dependency: ApiPackage, version: String): Boolean = when (dependency) {
            is ApiMavenPackage -> when (val apiMavenVersion = dependency.versions.all[version]) {
                is ApiMavenPackage.MavenVersion -> PackagesType.Maven in compatiblePackageTypes
                is ApiMavenPackage.GradleVersion -> compatiblePackageTypes.filterIsInstance<PackagesType.Gradle>()
                    .takeIf { it.isNotEmpty() }
                    ?.any { compatibleGradlePackageType ->
                        compatibleGradlePackageType.variants.all { requiredVariant ->
                            apiMavenVersion.variants.any { availableVariant ->
                                requiredVariant.attributes.all { (attributeName, attribute) ->
                                    availableVariant.attributes[attributeName]
                                        ?.let { attribute.isCompatible(it) } ?: false
                                }
                            }
                        }
                    } ?: false
                null -> false
            }
        }

        override fun getInstallData(
            apiPackage: ApiPackage,
            selectedVersion: String,
            selectedScope: String?
        ): KotlinMultiplatformInstallPackageData = when (apiPackage) {
            is ApiMavenPackage -> when (val apiMavenVersion = apiPackage.versions.all[selectedVersion]) {
                is ApiMavenVersion -> KotlinMultiplatformInstallPackageData(
                    apiPackage = apiPackage,
                    selectedVersion = apiMavenVersion.normalized.versionName,
                    selectedConfiguration = selectedScope ?: "implementation",
                    variantName = name
                )
                null -> error("Version $selectedVersion not found in package $apiPackage")
            }
        }
    }

    @Serializable
    data class DependenciesBlock(
        override val declaredDependencies: List<PackageSearchKotlinMultiplatformDeclaredDependency.Maven>,
        override val attributes: List<String>,
        override val compatiblePackageTypes: List<PackagesType>,
    ) : PackageSearchKotlinMultiplatformVariant {

        companion object {
            const val NAME = "dependencies block"
        }

        override val name: String = NAME
        override val variantTerminology = null

        override val isPrimary: Boolean
            get() = false

        override fun isCompatible(dependency: ApiPackage, version: String) = when (dependency) {
            is ApiMavenPackage -> true
            else -> false
        }

        override fun getInstallData(
            apiPackage: ApiPackage,
            selectedVersion: String,
            selectedScope: String?
        )= when (apiPackage) {
            is ApiMavenPackage -> when (val version = apiPackage.versions.all[selectedVersion]) {
                is ApiMavenVersion -> KotlinMultiplatformInstallPackageData(
                    apiPackage = apiPackage,
                    selectedVersion = version.normalized.versionName,
                    selectedConfiguration = selectedScope ?: "implementation",
                    variantName = name
                )
                null -> error("Version $selectedVersion not found in package $apiPackage")
            }
        }
    }

    @Serializable
    data class Cocoapods(
        override val declaredDependencies: List<PackageSearchKotlinMultiplatformDeclaredDependency.Cocoapods>,
        override val attributes: List<String>,
        override val compatiblePackageTypes: List<PackagesType>,
    ) : PackageSearchKotlinMultiplatformVariant {

        override val isPrimary: Boolean
            get() = false

        companion object {
            const val NAME = "cocoapods"
        }

        override val name: String = NAME
        override val variantTerminology = null

        override fun isCompatible(dependency: ApiPackage, version: String) = when (dependency) {
            is ApiMavenPackage -> false
        }

        override fun getInstallData(
            apiPackage: ApiPackage,
            selectedVersion: String,
            selectedScope: String?
        ) = when (apiPackage) {
            is ApiMavenPackage -> error("Cannot install a Maven package as a Cocoapods dependency")
        }
    }
}