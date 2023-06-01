package org.jetbrains.packagesearch.plugin

import com.intellij.openapi.components.Service
import org.jetbrains.packagesearch.client.PackageSearchApiClient
import org.jetbrains.packagesearch.plugin.utils.IntelliJApplication
import org.jetbrains.packagesearch.plugin.utils.PackageSearchApiEndpointsService

@Service(Service.Level.APP)
class PackageSearchApiClientService {
    val client = PackageSearchApiClient(
        endpoints = IntelliJApplication.PackageSearchApiEndpointsService.endpoints,
    )
}