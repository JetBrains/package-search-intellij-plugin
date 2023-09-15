package com.jetbrains.packagesearch.mock.server

import com.jetbrains.packagesearch.mock.MAVEN_CENTRAL_API_REPOSITORY
import com.jetbrains.packagesearch.mock.SonatypeApiClient
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.protobuf.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.packagesearch.api.v3.http.GetPackageInfoRequest
import org.jetbrains.packagesearch.api.v3.http.GetPackageInfoResponse
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesResponse
import org.slf4j.event.Level
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

@Suppress("FunctionName")
fun Application.PackageSearchMockServer() {

    val client = SonatypeApiClient()

    install(ServerContentNegotiation) {
        protobuf(ProtoBuf)
        json(Json {
            encodeDefaults = true
        })
    }
    install(CallLogging) {
        level = Level.TRACE
    }
    install(Compression) {
        gzip()
    }
    routing {
        route("api/v3") {
            get("search-packages") {
                val request = call.receive<SearchPackagesRequest>()
                val results = client.searchApiMavenPackages(request.searchQuery)
                call.respond(SearchPackagesResponse(request, results))
            }
            get("package-info-by-id-hashes") {
                call.respond<List<String>>(emptyList())
            }
            get("package-info-by-ids") {
                val ids = call.receive<GetPackageInfoRequest>().ids
                val packages = ids
                    .asFlow()
                    .buffer()
                    .mapNotNull {
                        if (!it.startsWith("maven:")) return@mapNotNull null
                        val (_, groupId, artifactId) = it.split(":")
                        client.getApiMavenPackage(groupId, artifactId)
                    }
                    .toList()
                call.respond(GetPackageInfoResponse(packages))
            }
            get("known-repositories") {
                call.respond(MAVEN_CENTRAL_API_REPOSITORY)
            }
        }
    }
}