@file:Suppress("FunctionName", "unused")

package com.jetbrains.packagesearch.plugin.core.data

import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchKnownRepositoriesContext
import com.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import java.nio.file.Path
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiPackageVersion
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType

/**
 * Package Search representation of a module.
 **/
@Serializable
sealed interface PackageSearchModule : IconProvider {

    val name: String
    val identity: Identity
    val buildFilePath: Path?
    val declaredKnownRepositories: Map<String, ApiRepository>

    val availableScopes: List<String>
    val defaultScope: String?

    val compatiblePackageTypes: List<PackagesType>
    val dependencyMustHaveAScope: Boolean

    val hasUpdates: Boolean
    val hasStableUpdates: Boolean

    interface WithVariants : PackageSearchModule {
        val variants: Map<String, PackageSearchModuleVariant>
        val mainVariant: PackageSearchModuleVariant

        override val hasUpdates: Boolean
            get() = variants.values.any { it.declaredDependencies.any { it.hasUpdates } }
        override val hasStableUpdates: Boolean
            get() = variants.values.any { it.declaredDependencies.any { it.hasStableUpdates } }
    }

    interface Base : PackageSearchModule, PackageInstallDataProvider {
        val declaredDependencies: List<PackageSearchDeclaredPackage>

        override val hasUpdates: Boolean
            get() = declaredDependencies.any { it.hasUpdates }
        override val hasStableUpdates: Boolean
            get() = declaredDependencies.any { it.hasStableUpdates }
    }

    @Serializable
    data class Identity(val group: String, val path: String)
}

fun PackageSearchModule.getPackageTypes() = when (this) {
    is PackageSearchModule.Base -> compatiblePackageTypes
    is PackageSearchModule.WithVariants -> {
        variants.entries.flatMap { it.value.compatiblePackageTypes }
    }
}

interface PackageSearchDependencyManager {

    suspend fun updateDependencies(
        context: PackageSearchKnownRepositoriesContext,
        data: List<UpdatePackageData>,
    )

    suspend fun addDependency(
        context: PackageSearchKnownRepositoriesContext,
        data: InstallPackageData,
    )

    suspend fun removeDependency(
        context: ProjectContext,
        data: RemovePackageData,
    )
}

interface UpdatePackageData {
    val installedPackage: PackageSearchDeclaredPackage
    val newVersion: String?
    val newScope: String?
}

interface InstallPackageData {
    val apiPackage: ApiPackage
    val selectedVersion: ApiPackageVersion
}

interface RemovePackageData {
    val declaredPackage: PackageSearchDeclaredPackage
}

interface PackageInstallDataProvider {
    fun getInstallData(
        apiPackage: ApiPackage,
        selectedVersion: ApiPackageVersion,
        selectedScope: String? = null,
    ): InstallPackageData
}