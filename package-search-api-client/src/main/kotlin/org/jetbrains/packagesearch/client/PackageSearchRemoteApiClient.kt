package org.jetbrains.packagesearch.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.MavenHashLookupRequest
import org.jetbrains.packagesearch.api.v3.MavenHashLookupResponse
import org.jetbrains.packagesearch.api.v3.http.GetPackageInfoRequest
import org.jetbrains.packagesearch.api.v3.http.GetScmByUrlRequest
import org.jetbrains.packagesearch.api.v3.search.SearchParameters

interface PackageSearchApiClient {

    val endpoints: PackageSearchEndpoints

    suspend fun getKnownRepositories(): List<ApiRepository>

    suspend fun getPackageInfoByIds(ids: Set<String>): List<ApiPackage>

    suspend fun getPackageInfoByIdHashes(ids: Set<String>): List<ApiPackage>

    suspend fun searchPackages(searchParameters: SearchParameters): List<ApiPackage>

    suspend fun getMavenPackageInfoByFileHash(request: MavenHashLookupRequest): MavenHashLookupResponse

    suspend fun getScmByUrl(request: GetScmByUrlRequest): String?
}

object MockData {
    val repositories by getResourceAsText("mocks/repositories.json")
    val packages by getResourceAsText("mocks/packages.json")
    val scms by getResourceAsText("mocks/scms.json")
    val searchResult1 by getResourceAsText("mocks/repositories.json")
    val searchResult2 by getResourceAsText("mocks/repositories.json")

    private fun getResourceAsText(path: String) =
        lazy {
            MockData::class.java.getResource(path)
                ?.readText()
                ?: error("Resource not found: $path")
        }
}

class PackageSearchMockApiClient(
    override val endpoints: PackageSearchEndpoints,
    val mockData: MockData,
) : PackageSearchApiClient {

    val httpClient = HttpClient(MockEngine) {
        engine {
            addHandler {
                when (it.url) {
                    endpoints.searchPackages ->
                }
            }
        }
    }

    override suspend fun getKnownRepositories(): List<ApiRepository> {
        TODO("Not yet implemented")
    }

    override suspend fun getPackageInfoByIds(ids: Set<String>): List<ApiPackage> {
        TODO("Not yet implemented")
    }

    override suspend fun getPackageInfoByIdHashes(ids: Set<String>): List<ApiPackage> {
        TODO("Not yet implemented")
    }

    override suspend fun searchPackages(searchParameters: SearchParameters): List<ApiPackage> {
        TODO("Not yet implemented")
    }

    override suspend fun getMavenPackageInfoByFileHash(request: MavenHashLookupRequest): MavenHashLookupResponse {
        TODO("Not yet implemented")
    }

    override suspend fun getScmByUrl(request: GetScmByUrlRequest): String? {
        TODO("Not yet implemented")
    }
}

class PackageSearchRemoteApiClient(override val endpoints: PackageSearchEndpoints) : PackageSearchApiClient {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    override suspend fun getKnownRepositories(): List<ApiRepository> =
        httpClient.get(endpoints.knownRepositories).body()

    override suspend fun getPackageInfoByIds(ids: Set<String>): List<ApiPackage> =
        httpClient.get(endpoints.packageInfoByIds) { setBody(GetPackageInfoRequest(ids)) }
            .body()

    override suspend fun getPackageInfoByIdHashes(ids: Set<String>): List<ApiPackage> =
        httpClient.get(endpoints.packageInfoByIdHashes) { setBody(GetPackageInfoRequest(ids)) }
            .body()

    override suspend fun searchPackages(searchParameters: SearchParameters): List<ApiPackage> =
        httpClient.get(endpoints.searchPackages) { setBody(searchParameters) }
            .body()

    override suspend fun getMavenPackageInfoByFileHash(request: MavenHashLookupRequest): MavenHashLookupResponse =
        httpClient.get(endpoints.mavenPackageInfoByFileHash) { setBody(request) }
            .body()

    override suspend fun getScmByUrl(request: GetScmByUrlRequest): String? =
        httpClient.get(endpoints.getScmByUrl) { setBody(request) }
            .body()

}

suspend fun PackageSearchApiClient.getScmByUrl(url: String): String? =
    getScmByUrl(GetScmByUrlRequest(url))

fun buildUrl(action: URLBuilder.() -> Unit) = URLBuilder().apply(action).build()

interface PackageSearchEndpoints {
    val knownRepositories: Url
    val packageInfoByIds: Url
    val packageInfoByIdHashes: Url
    val searchPackages: Url
    val getScmByUrl: Url
    val mavenPackageInfoByFileHash: Url
}

fun CoroutineScope.lol() {
    this.
}