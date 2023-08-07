package com.jetbrains.packagesearch.plugin.core.extensions

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineNitrite

interface PackageSearchKnownRepositoriesProvider {
    val knownRepositories: Map<String, ApiRepository>
}

interface PackageSearchApiPackagesProvider {

    /**
     * Collect first all usages across every module, then call this function.
     *
     * @param packageIds the list of package ids to fetch.
     * @return a map of package id to [ApiPackage] instance.
     */
    suspend fun getPackageInfoByIds(packageIds: Set<String>): Map<String, ApiPackage>
    suspend fun getPackageInfoByIdHashes(packageIdHashes: Set<String>): Map<String, ApiPackage>
}

interface PackageSearchModuleBuilderContext :
    ProjectContext, PackageSearchKnownRepositoriesProvider, PackageSearchApiPackagesProvider {
        val projectCaches: CoroutineNitrite
        val applicationCaches: CoroutineNitrite
    }

interface ProjectContext {
    val project: Project
    val coroutineScope: CoroutineScope
}