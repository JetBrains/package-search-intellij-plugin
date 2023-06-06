package org.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.components.Service
import org.jetbrains.packagesearch.client.PackageSearchApiClient
import org.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import org.jetbrains.packagesearch.plugin.utils.PackageSearchApiEndpointsService

@Service(Service.Level.APP)
class PackageSearchApiClientService {
    val client = PackageSearchApiClient(
        endpoints = IntelliJApplication.PackageSearchApiEndpointsService.endpoints,
    )
}