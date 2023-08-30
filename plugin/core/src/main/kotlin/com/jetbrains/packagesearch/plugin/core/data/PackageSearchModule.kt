@file:Suppress("FunctionName", "unused")

package com.jetbrains.packagesearch.plugin.core.data

import com.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType

/**
 * Package Search representation of a module.
 **/
@Serializable
sealed interface PackageSearchModule : WithIcon {

    val name: String
    val identity: Identity
    val buildFilePath: String?
    val declaredKnownRepositories: Map<String, ApiRepository>
    val availableScopes: List<String>
    val defaultScope: String?
    val compatiblePackageTypes: List<PackagesType>

    interface WithVariants : PackageSearchModule {
        val variants: Map<String, PackageSearchModuleVariant>
        val mainVariant: PackageSearchModuleVariant
    }

    interface Base : PackageSearchModule, PackageInstallDataProvider {
        val declaredDependencies: List<PackageSearchDeclaredPackage>
    }

    @Serializable
    data class Identity(val group: String, val path: String)
}

fun PackageSearchModule.getPackageTypes()= when(this){
    is PackageSearchModule.Base -> compatiblePackageTypes
    is PackageSearchModule.WithVariants->{
        variants.entries.flatMap { it.value.compatiblePackageTypes }
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