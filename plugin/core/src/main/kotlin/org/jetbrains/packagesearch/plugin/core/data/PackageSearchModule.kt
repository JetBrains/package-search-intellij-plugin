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
    val identityPath: String
    val buildFilePath: String?
    val declaredKnownRepositories: Map<String, ApiRepository>
    val availableScopes: List<String>
    val defaultScope: String?

    interface WithVariants : PackageSearchModule {
        val variants: Map<String, PackageSearchModuleVariant>
    }

    interface Base : PackageSearchModule, PackageInstallDataProvider {
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
    val newScope: String?
}

interface InstallPackageData {
    val apiPackage: ApiPackage
    val selectedVersion: String
}

interface RemovePackageData {
    val declaredPackage: PackageSearchDeclaredPackage
}

interface PackageInstallDataProvider {
    fun getInstallData(apiPackage: ApiPackage, selectedVersion: String, selectedScope: String? = null): InstallPackageData
}