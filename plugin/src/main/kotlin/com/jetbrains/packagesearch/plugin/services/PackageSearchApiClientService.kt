package com.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.components.Service
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.utils.KtorDebugLogger
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiEndpointsService
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient

@Service(Service.Level.APP)
class PackageSearchApiClientService {
    val client = PackageSearchApiClient(
        endpoints = IntelliJApplication.PackageSearchApiEndpointsService.endpoints,
        httpClient = PackageSearchApiClient.defaultHttpClient(false) {
            install(Logging) {
                level = LogLevel.HEADERS
                logger = KtorDebugLogger()
            }
        }
    )
}