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

    interface Base : PackageSearchModule, DependencyManager {
        val compatiblePackageTypes: List<PackagesType>
        val declaredDependencies: List<PackageSearchDeclaredPackage>
    }

}

interface DependencyManager {
    suspend fun updateDependencies(
        context: ProjectContext,
        updateCandidates: List<PackageUpdate>,
        knownRepositories: List<ApiRepository>
    )

    suspend fun installDependency(
        context: ProjectContext,
        apiPackage: ApiPackage,
        selectedVersion: String,
        selectedScope: String? = null
    )

    suspend fun removeDependency(
        context: ProjectContext,
        installedPackage: PackageSearchDeclaredPackage,
    )
}

interface PackageUpdate {
    val installedPackage: PackageSearchDeclaredPackage
    val version: String?
}

