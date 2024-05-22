package com.jetbrains.packagesearch.plugin.tests.unit

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.packagesearch.api.v3.http.PackageSearchEndpointPaths

suspend fun getResource(resource: String) = withContext(Dispatchers.IO) {
    CachesTest::class.java.getResourceAsStream(resource)
        ?: error("Resource not found: $resource")
}

suspend fun MockRequestHandleScope.handleHttpRequest(it: HttpRequestData) =
    when (it.url.pathSegments.filterNot { it.isBlank() }.first()) {
        PackageSearchEndpointPaths.packageInfoByIds, PackageSearchEndpointPaths.packageInfoByIdHashes, PackageSearchEndpointPaths.refreshPackagesInfo ->
            respondJson("/mock/responses/package_info.json")

        else -> error("Unhandled ${it.url}")
    }

suspend fun MockRequestHandleScope.respondJson(resourcePath: String) = respond(
    content = getResource(resourcePath).toByteReadChannel(),
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
)