import io.ktor.client.call.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.packagesearch.api.v3.http.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Duration.Companion.minutes

class ProtobufTest {

    val httpClient = PackageSearchApiClient.defaultHttpClient {
        install(Logging) {
            level = LogLevel.BODY
        }
    }

    val endpoints = object : PackageSearchEndpoints {
        override val knownRepositories get() = TODO()
        override val packageInfoByIds = buildUrl {
            protocol = URLProtocol.HTTP
            host = "localhost"
            port = 8081
            encodedPath = "/api/v3/package-info-by-ids"
        }
        override val packageInfoByIdHashes get() = TODO()
        override val searchPackages get() = TODO()
        override val getScmsByUrl get() = TODO()
        override val mavenPackageInfoByFileHash get() = TODO()
    }

    val apiClient = PackageSearchApiClient(endpoints, httpClient)
    val json = Json { prettyPrint = true }

    @Test
    fun test() = runTest(timeout = 1.minutes) {
//        val protobufPackages = async {
//            httpClient.get(endpoints.packageInfoByIds) {
//                contentType(ContentType.Application.Json)
//                setBody(GetPackageInfoRequest(setOf("maven:io.ktor:ktor-client-js")))
//            }.body<GetPackageInfoResponse>()
//        }
        val jsonPackages = async {
            httpClient.get(endpoints.packageInfoByIds) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(GetPackageInfoRequest(setOf("maven:io.ktor:ktor-client-js")))
            }.body<GetPackageInfoResponse>()
        }
//        File("C:\\Users\\lamba\\IdeaProjects\\pkgs-plugin-v2\\packagesearch-api-mock-server\\proto.json")
//            .writeText(json.encodeToString(protobufPackages.await()))
        File("C:\\Users\\lamba\\IdeaProjects\\pkgs-plugin-v2\\packagesearch-api-mock-server\\json.json")
            .writeText(json.encodeToString(jsonPackages.await()))
    }

}