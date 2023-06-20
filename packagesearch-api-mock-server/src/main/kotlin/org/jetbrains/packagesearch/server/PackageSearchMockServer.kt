package org.jetbrains.packagesearch.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.client.plugins.logging.Logging
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.async
import kotlinx.datetime.Clock
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.ApiRepository
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
    }

    val pomResolver = PomResolver(xml = xml, httpClient = client)

    install(ServerContentNegotiation) {
        json()
    }
    install(CallLogging) {
        level = Level.INFO
    }

    val packages = async { getPackages(client, pomResolver) }

    routing {
        route("api/v3") {
            get("search-packages") {
                call.respond(packages.await())
            }
            get("package-info-by-id-hashes") {
                call.respond(packages.await())
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


