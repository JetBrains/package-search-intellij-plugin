package org.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.registry.Registry
import io.ktor.http.URLProtocol
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.packagesearch.api.v3.http.PackageSearchDefaultEndpoints

@Service(Service.Level.APP)
class PackageSearchApiEndpointsService(private val scope: CoroutineScope) {

    companion object {
        private val isLocal
            get() = System.getenv("USE_LOCAL_MOCKS")
                ?.let { it == "true" }
                ?: Registry.`is`("org.jetbrains.packagesearch.localhost", false)
    }

    val endpoints = PackageSearchDefaultEndpoints(
        protocol = if (isLocal) URLProtocol.HTTP else URLProtocol.HTTPS,
        if (isLocal) "localhost" else "packagesearch.jetbrains.com",
        if (isLocal) 8081 else URLProtocol.HTTPS.defaultPort
    )
}