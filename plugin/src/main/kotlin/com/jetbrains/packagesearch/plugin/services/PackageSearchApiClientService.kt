package com.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.jetbrains.packagesearch.mock.SonatypeApiClient
import com.jetbrains.packagesearch.mock.client.PackageSearchSonatypeApiClient
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.registryFlow
import com.jetbrains.packagesearch.plugin.utils.KtorDebugLogger
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApi
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchEndpoints

@Service(Level.APP)
class PackageSearchApiClientService(coroutineScope: CoroutineScope) {

    val client = IntelliJApplication
        .registryFlow("org.jetbrains.packagesearch.sonatype")
        .map { if (it) PackageSearchApiClientType.Sonatype else PackageSearchApiClientType.Dev }
        .stateIn(coroutineScope, SharingStarted.Eagerly, PackageSearchApiClientType.Sonatype)

}

sealed interface PackageSearchApiClientType {

    val client: PackageSearchApi

    data object Sonatype : PackageSearchApiClientType {
        override val client: PackageSearchApi = PackageSearchSonatypeApiClient(
            httpClient = SonatypeApiClient.defaultHttpClient {
                install(Logging) {
                    level = LogLevel.INFO
                    logger = KtorDebugLogger()
                }
            }
        )
    }

    data object Dev : PackageSearchApiClientType {
        override val client: PackageSearchApi = PackageSearchApiClient(
            endpoints = PackageSearchEndpoints.DEV,
            httpClient = PackageSearchApiClient.defaultHttpClient {
                install(Logging) {
                    level = LogLevel.INFO
                    logger = KtorDebugLogger()
                }
            }
        )
    }
}
