package org.jetbrains.packagesearch.server

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.protobuf.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.http.GetPackageInfoRequest
import org.jetbrains.packagesearch.api.v3.http.GetPackageInfoResponse
import org.jetbrains.packagesearch.maven.PomResolver
import org.slf4j.event.Level
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation.Plugin as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

@Suppress("FunctionName")
fun Application.PackageSearchMockServer() {

    val xml = XML {
        indentString = "    "
        defaultPolicy {
            ignoreUnknownChildren()
        }
    }

    val client = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json()
            xml(xml)
        }
        install(Logging) {
            level = LogLevel.HEADERS
        }
        install(HttpRequestRetry) {
            maxRetries = 10
            constantDelay(500, 100, false)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 5000
        }
    }

    val pomResolver = PomResolver(xml = xml, httpClient = client)

    install(ServerContentNegotiation) {
        protobuf(ProtoBuf)
        json(Json {
            encodeDefaults = false
            explicitNulls = false
        })
    }
    install(CallLogging) {
        level = Level.INFO
    }
    install(Compression) {
        gzip()
    }

    routing {
        route("api/v3") {
            get("search-packages") {
                val apiModels = pomResolver.toApiModels(
                    listOf(
                        "maven:io.ktor:ktor-client-cio",
                        "maven:io.ktor:ktor-client-js",
                        "maven:io.ktor:ktor-client-darwin",
                        "maven:io.ktor:ktor-server-cio",
                    )
                )
                call.respond(apiModels)
            }
            get("package-info-by-id-hashes") {
                call.respond<List<String>>(emptyList())
            }
            get("package-info-by-ids") {
                val packages = pomResolver.toApiModels(call.receive<GetPackageInfoRequest>().ids)
                call.respond(GetPackageInfoResponse(packages))
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