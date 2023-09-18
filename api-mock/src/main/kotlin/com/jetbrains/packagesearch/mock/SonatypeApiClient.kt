package com.jetbrains.packagesearch.mock

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.core.Closeable
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.maven.GoogleMavenCentralMirror
import org.jetbrains.packagesearch.maven.MavenUrlBuilder
import org.jetbrains.packagesearch.maven.PomResolver
import org.jetbrains.packagesearch.maven.central.MavenCentralApiResponse

class SonatypeApiClient(
    xml: XML = DEFAULT_XML,
    private val httpClient: HttpClient = defaultHttpClient(xml),
    private val pomResolver: PomResolver = defaultPomResolver(xml, httpClient),
) : Closeable by httpClient {

    companion object {
        val DEFAULT_XML
            get() = XML {
                indentString = "    "
                defaultPolicy {
                    ignoreUnknownChildren()
                }
            }

        fun defaultHttpClient(
            xml: XML = DEFAULT_XML,
            additionalConfig: HttpClientConfig<CIOEngineConfig>.() -> Unit = {},
        ) = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
                xml(xml)
            }
            install(HttpTimeout) {
                requestTimeout = 10.seconds
            }
//            install(HttpRequestRetry) {
//                retryOnExceptionOrServerErrors(maxRetries = 5)
//                delayMillis { 500 }
//            }
            additionalConfig()
        }

        fun defaultPomResolver(
            xml: XML,
            httpClient: HttpClient,
            repositories: List<MavenUrlBuilder> = listOf(GoogleMavenCentralMirror),
        ) = PomResolver(repositories, xml, httpClient)
    }


    private fun getSearchUrl(searchQuery: String, rows: Int = 5) =
        "https://search.maven.org/solrsearch/select?q=$searchQuery&rows=$rows&wt=json"

    private fun getPackageInfoUrl(groupId: String, artifactId: String, rows: Int = 5) =
        "https://search.maven.org/solrsearch/select?q=g:$groupId+AND+a:$artifactId&core=gav&rows=$rows&wt=json"

    suspend fun searchPackages(query: String, packagesCount: Int = 5): MavenCentralApiResponse =
        httpClient.get(getSearchUrl(query, packagesCount)).body()

    suspend fun getPackageInfo(groupId: String, artifactId: String, versionCount: Int = 5): MavenCentralApiResponse =
        httpClient.get(getPackageInfoUrl(groupId, artifactId, versionCount)).body()

    suspend fun getApiMavenPackage(groupId: String, artifactId: String, versionCount: Int = 5): ApiMavenPackage =
        httpClient.get(getPackageInfoUrl(groupId, artifactId, versionCount)).body<MavenCentralApiResponse>()
            .let { httpClient.getBuildSystemsMetadata(it, pomResolver) }
            .toMavenApiModel()

    suspend fun searchApiMavenPackages(
        query: String,
        packagesCount: Int = 5,
        versionCount: Int = 5,
        onTransformError: suspend FlowCollector<ApiMavenPackage>.(cause: Throwable) -> Unit = {},
    ): List<ApiMavenPackage> = searchPackages(query, packagesCount)
        .response
        .docs
        .asFlow()
        .buffer()
        .map { getPackageInfo(it.groupId, it.artifactId, versionCount) }
        .buffer()
        .map { httpClient.getBuildSystemsMetadata(it, pomResolver) }
        .buffer()
        .map { it.toMavenApiModel() }
        .catch(onTransformError)
        .toList()
}
