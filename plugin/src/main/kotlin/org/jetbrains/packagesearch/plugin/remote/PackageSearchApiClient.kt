package org.jetbrains.packagesearch.plugin.remote

import org.jetbrains.packagesearch.api.v3.MavenHashLookupRequest
import org.jetbrains.packagesearch.api.v3.MavenHashLookupResponse
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.SearchParameters

interface PackageSearchApiClient {

    suspend fun getKnownRepositories(): List<ApiRepository>
    suspend fun getPackageInfoByIds(ids: Set<String>): List<ApiPackage>
    suspend fun getPackageInfoByIdHashes(ids: Set<String>): List<ApiPackage>
    suspend fun searchPackages(searchParameters: SearchParameters): List<ApiPackage>
    suspend fun getMavenPackageInfoByFileHash(request: MavenHashLookupRequest): MavenHashLookupResponse

}