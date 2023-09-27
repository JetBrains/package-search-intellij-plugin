package com.jetbrains.packagesearch.mock.client

import com.jetbrains.packagesearch.mock.MAVEN_CENTRAL_API_REPOSITORY
import com.jetbrains.packagesearch.mock.SonatypeApiClient
import io.ktor.client.HttpClient
import java.io.Closeable
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiProject
import org.jetbrains.packagesearch.api.v3.MavenHashLookupRequest
import org.jetbrains.packagesearch.api.v3.MavenHashLookupResponse
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApi
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest
import org.jetbrains.packagesearch.api.v3.http.SearchProjectRequest
import org.jetbrains.packagesearch.maven.PomResolver

class PackageSearchSonatypeApiClient(
    xml: XML = SonatypeApiClient.DEFAULT_XML,
    httpClient: HttpClient = SonatypeApiClient.defaultHttpClient(xml),
    pomResolver: PomResolver = SonatypeApiClient.defaultPomResolver(xml, httpClient),
    private val onError: suspend (cause: Throwable) -> Unit = {},
) : PackageSearchApi, Closeable {

    private val sonatypeApiClient = SonatypeApiClient(xml, httpClient, pomResolver)

    override fun close() = sonatypeApiClient.close()

    override suspend fun getKnownRepositories() =
        MAVEN_CENTRAL_API_REPOSITORY

    override suspend fun getPackageInfoByIds(ids: Set<String>): Map<String, ApiPackage> =
        ids.asFlow()
            .filter { it.startsWith("maven:") }
            .map { id -> id.split(":") }
            .buffer()
            .mapNotNull { (_, groupId, artifactId) ->
                sonatypeApiClient.getApiMavenPackage(groupId, artifactId)
            }
            .catch { onError(it) }
            .toList()
            .associateBy { it.id }

    override suspend fun getPackageInfoByIdHashes(ids: Set<String>): Map<String, ApiPackage> =
        emptyMap()

    override suspend fun searchPackages(request: SearchPackagesRequest): List<ApiPackage> =
        sonatypeApiClient.searchApiMavenPackages(request.searchQuery)

    override suspend fun searchPackageIds(request: SearchPackagesRequest): List<String> =
        sonatypeApiClient.searchPackages(request.searchQuery)
            .response
            .docs
            .map { "maven:${it.groupId}:${it.artifactId}" }

    override suspend fun searchProjects(request: SearchProjectRequest): List<ApiProject> =
        emptyList()

    override suspend fun getMavenPackageInfoByFileHash(request: MavenHashLookupRequest): MavenHashLookupResponse {
        error("cannot be implemented through Sonatype APIs")
    }

}