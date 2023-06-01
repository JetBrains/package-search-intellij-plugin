package org.jetbrains.packagesearch.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.MavenHashLookupRequest
import org.jetbrains.packagesearch.api.v3.MavenHashLookupResponse
import org.jetbrains.packagesearch.api.v3.http.GetPackageInfoRequest
import org.jetbrains.packagesearch.api.v3.http.GetScmByUrlRequest
import org.jetbrains.packagesearch.api.v3.search.SearchParameters

class PackageSearchApiClient(val endpoints: PackageSearchEndpoints) {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun getKnownRepositories(): List<ApiRepository> =
        httpClient.get(endpoints.knownRepositories).body()

    suspend fun getPackageInfoByIds(ids: Set<String>): List<ApiPackage> =
        httpClient.get(endpoints.packageInfoByIds) { setBody(GetPackageInfoRequest(ids)) }
            .body()

    suspend fun getPackageInfoByIdHashes(ids: Set<String>): List<ApiPackage> =
        httpClient.get(endpoints.packageInfoByIdHashes) { setBody(GetPackageInfoRequest(ids)) }
            .body()

    suspend fun searchPackages(searchParameters: SearchParameters): List<ApiPackage> =
        httpClient.get(endpoints.searchPackages) { setBody(searchParameters) }
            .body()

    suspend fun getMavenPackageInfoByFileHash(request: MavenHashLookupRequest): MavenHashLookupResponse =
        httpClient.get(endpoints.mavenPackageInfoByFileHash) { setBody(request) }
            .body()

    suspend fun getScmByUrl(request: GetScmByUrlRequest): String? =
        httpClient.get(endpoints.getScmsByUrl) { setBody(request) }
            .body()

}

