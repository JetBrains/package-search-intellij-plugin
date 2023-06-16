@file:Suppress("FunctionName", "unused")

package org.jetbrains.packagesearch.plugin.core.data

import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import org.jetbrains.packagesearch.plugin.core.extensions.ProjectContext

/**
 * Package Search representation of a module.
 **/
@Serializable
sealed interface PackageSearchModule : WithIcon {

    val name: String
    val projectDirPath: String
    val buildFilePath: String?
    val declaredKnownRepositories: Map<String, ApiRepository>
    val defaultScope: String?

    interface WithVariants : PackageSearchModule {
        val variantTerminology: String
        val declaredDependencies: List<PackageSearchModuleVariant>
    }

    interface Base : PackageSearchModule {
        val compatiblePackageTypes: List<PackagesType>
        val declaredDependencies: List<PackageSearchDeclaredDependency>
    }

    suspend fun updateDependencies(
        context: ProjectContext,
        installedPackages: List<PackageSearchDeclaredDependency>,
        knownRepositories: List<ApiRepository>,
        onlyStable: Boolean,
    )

    suspend fun installDependency(
        context: ProjectContext,
        apiPackage: ApiPackage,
        selectedVersion: String,
    )

    suspend fun removeDependency(
        context: ProjectContext,
        installedPackage: PackageSearchDeclaredDependency,
    )

}

