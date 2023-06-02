package org.jetbrains.packagesearch.server

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import org.jetbrains.packagesearch.api.v3.*
import org.jetbrains.packagesearch.api.v3.ApiGradlePackage.ApiVariant.WithAvailableAt
import org.jetbrains.packagesearch.api.v3.ApiGradlePackage.ApiVariant.WithAvailableAt.AvailableAt
import org.jetbrains.packagesearch.api.v3.ApiGradlePackage.ApiVariant.WithFiles
import org.jetbrains.packagesearch.api.v3.ApiGradlePackage.GradleVersion
import org.jetbrains.packagesearch.gradlemetadata.File
import org.jetbrains.packagesearch.gradlemetadata.GradleMetadata
import org.jetbrains.packagesearch.gradlemetadata.Variant
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.slf4j.event.*
import io.ktor.client.engine.cio.CIO as CIOClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ContentNegotiationClient
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ContentNegotiationServer
import org.jetbrains.packagesearch.gradlemetadata.Dependency as GradleMetadataDependency

suspend fun main(): Unit = coroutineScope {
    embeddedServer(CIO, port = 8081, module = Application::PackageSearchMockServer).start()
}

val searchUrls = listOf(
    "https://repo1.maven.org/maven2/io/ktor/ktor-client-cio/2.3.0/ktor-client-cio-2.3.0",
    "https://repo1.maven.org/maven2/io/ktor/ktor-server-cio/2.3.0/ktor-server-cio-2.3.0",
)

@Suppress("FunctionName")
fun Application.PackageSearchMockServer() {

    val client = HttpClient(CIOClient) {
        install(ContentNegotiationClient) {
            json()
        }
    }

    install(ContentNegotiationServer) {
        json()
    }
    install(CallLogging) {
        level = Level.INFO
    }

    routing {
        route("api/v3") {
            get("search-packages") {
                call.respond<List<ApiPackage>>(
                    searchUrls
                        .map { client.get("$it.module").body<GradleMetadata>() }
                        .map { it.toGradleApiPackage() }
                )
            }
            get("package-info-by-id-hashes") {
                call.respond<List<ApiPackage>>(
                    searchUrls
                        .map { client.get("$it.module").body<GradleMetadata>() }
                        .map { it.toGradleApiPackage() }
                )
            }
            get("known-repositories") {
                call.respond(
                    listOf<ApiRepository>(
                        ApiMavenRepository(
                            id = "maven-central",
                            lastChecked = Clock.System.now(),
                            url = "https://repo.maven.apache.org/maven2",
                            alternateUrls = emptyList(),
                            friendlyName = "Maven Central",
                            userFacingUrl = null,
                            packageCount = 0,
                            artifactCount = 0,
                            namedLinks = null
                        )
                    )
                )
            }
        }


    }
}

private fun GradleMetadata.toGradleApiPackage(): ApiPackage = ApiGradlePackage(
    id = "maven:${component.group}:${component.module}",
    idHash = ApiPackage.hashPackageId("maven:${component.group}:${component.module}"),
    name = component.module,
    description = null,
    authors = emptyList(),
    scmUrl = null,
    licenses = null,
    rankingMetric = 20.0,
    groupId = component.group,
    artifactId = component.module,
    versions = listOf(
        GradleVersion(
            normalized = NormalizedVersion.from(component.version, Clock.System.now()),
            repositoryIds = listOf("maven-central"),
            vulnerability = Vulnerability(
                isVulnerable = false,
                issues = emptyList()
            ),
            variants = variants?.map { it.toApiModel() } ?: emptyList(),
            parentComponent = component.url,
            dependencies = emptyList(),
            artifacts = emptyList()
        )
    )
)


fun Variant.toApiModel(): ApiGradlePackage.ApiVariant = when {
    availableAt != null -> WithAvailableAt(
        name = name,
        attributes = attributes ?: emptyMap(),
        availableAt = AvailableAt(
            url = availableAt!!.url,
            group = availableAt!!.group,
            module = availableAt!!.module,
            version = availableAt!!.version
        )
    )

    else -> WithFiles(
        name = name,
        attributes = attributes ?: emptyMap(),
        dependencies = dependencies?.map { it.toApiModel() } ?: emptyList(),
        files = files?.map { it.toApiModel() } ?: emptyList()
    )
}

fun GradleMetadataDependency.toApiModel() =
    ApiGradlePackage.ApiGradleDependency(
        group,
        module,
        version.requires
    )

fun File.toApiModel() = ApiGradlePackage.ApiVariant.File(
    name = name,
    url = url,
    size = size.toLong(),
    sha256 = sha256,
    md5 = md5,
    sha512 = sha512,
    sha1 = sha1
)