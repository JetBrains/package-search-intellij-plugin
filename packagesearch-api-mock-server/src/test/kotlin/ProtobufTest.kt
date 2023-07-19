import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.URLProtocol
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient.Companion.defaultHttpClientConfig
import org.jetbrains.packagesearch.api.v3.http.PackageSearchDefaultEndpoints
import org.jetbrains.packagesearch.server.PackageSearchMockServer
import org.junit.jupiter.api.Test

class ProtobufTest {

    @Test
    fun `json vs proto`() = withPkgsMockServer { protoClient, jsonClient ->
        val ids = setOf("maven:io.ktor:ktor-client-cio")
        val protobufPackages = async { protoClient.getPackageInfoByIds(ids) }
        val jsonPackages = jsonClient.getPackageInfoByIds(ids)
        assertEquals(protobufPackages.await(), jsonPackages)
    }
}

fun withPkgsMockServer(
    test: suspend CoroutineScope.(PackageSearchApiClient, PackageSearchApiClient) -> Unit
) = testApplication {
    application {
        PackageSearchMockServer()
    }
    val endpoints = PackageSearchDefaultEndpoints(
        protocol = URLProtocol.HTTP,
        host = "localhost",
        port = 80
    )
    coroutineScope {
        test(
            PackageSearchApiClient(
                endpoints = endpoints,
                httpClient = createClient {
                    defaultHttpClientConfig(true)
                    install(Logging) {
                        level = LogLevel.ALL
                    }
                }
            ),
            PackageSearchApiClient(
                endpoints = endpoints,
                httpClient = createClient {
                    defaultHttpClientConfig(false)
                    install(Logging) {
                        level = LogLevel.ALL
                    }
                }
            )
        )
    }
}
