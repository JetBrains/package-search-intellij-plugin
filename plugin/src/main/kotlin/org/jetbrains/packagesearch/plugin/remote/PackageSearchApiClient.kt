package org.jetbrains.packagesearch.plugin.remote

import io.github.classgraph.PackageInfo
import org.jetbrains.packagesearch.api.v3.Package
import org.jetbrains.packagesearch.api.v3.Repository

interface PackageSearchApiClient {
    suspend fun getKnownRepository(): List<Repository>
    suspend fun getPackageInfoByIds(ids: List<String>): List<Package>
    suspend fun searchPackages(
        query: String
    ): List<Package>
}