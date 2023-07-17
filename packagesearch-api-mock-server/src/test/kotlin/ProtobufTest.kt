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
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.packagesearch.api.v3.search.buildSearchParameters

class ProtobufTest {

    val httpClient = PackageSearchApiClient.defaultHttpClient {
        install(Logging) {
            level = LogLevel.BODY
        }
    }

    val endpoints = PackageSearchDefaultEndpoints(URLProtocol.HTTP, "localhost", 8081)

    val apiClient = PackageSearchApiClient(endpoints, httpClient)
    val json = Json { prettyPrint = true }

    @Test
    fun test() = runTest(timeout = 1.minutes) {
        val protobufPackages = apiClient.searchPackages(buildSearchParameters {
            searchQuery = "lol"
            packagesType {
                mavenPackages()
            }
        })
        println(protobufPackages)
    }

}