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
    val availableScopes: List<String>
    val defaultScope: String?

    interface WithVariants : PackageSearchModule {
        val variants: List<PackageSearchModuleVariant>
    }

    interface Base : PackageSearchModule {
        val compatiblePackageTypes: List<PackagesType>
        val declaredDependencies: List<PackageSearchDeclaredPackage>
    }

}

interface PackageSearchDependencyManager {

    suspend fun updateDependencies(
        context: ProjectContext,
        data: List<UpdatePackageData>,
        knownRepositories: List<ApiRepository>
    )

    suspend fun installDependency(
        context: ProjectContext,
        data: InstallPackageData
    )

    suspend fun removeDependency(
        context: ProjectContext,
        data: RemovePackageData
    )
}

interface UpdatePackageData {
    val installedPackage: PackageSearchDeclaredPackage
    val newVersion: String?
}

interface InstallPackageData {
    val apiPackage: ApiPackage
    val selectedVersion: String
}

interface RemovePackageData {
    val declaredPackage: PackageSearchDeclaredPackage
}