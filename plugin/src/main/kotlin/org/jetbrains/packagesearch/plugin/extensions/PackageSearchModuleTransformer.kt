package org.jetbrains.packagesearch.plugin.extensions

import com.intellij.openapi.module.Module
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.plugin.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.data.PackageSearchModule

interface PackageSearchModuleTransformer {

    val moduleSerializer: KSerializer<out PackageSearchModule>
    val versionSerializer: KSerializer<out PackageSearchDeclaredDependency>

    context(PackageSearchModuleBuilderContext)
    fun buildModule(nativeModule: Module): Flow<PackageSearchModule?>

    context(ProjectContext)
    suspend fun updateDependencies(
        module: PackageSearchModule,
        installedPackages: List<PackageSearchDeclaredDependency>,
        knownRepositories: List<ApiRepository>,
    )

    context(ProjectContext)
    suspend fun installDependency(
        module: PackageSearchModule,
        apiPackage: ApiPackage,
        selectedVersion: String = apiPackage.versions.latest.versionName,
    )

    context(ProjectContext)
    suspend fun removeDependency(
        module: PackageSearchModule,
        installedPackage: PackageSearchDeclaredDependency,
    )

}