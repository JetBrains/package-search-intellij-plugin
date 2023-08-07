package com.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.components.Service
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiEndpointsService

@Service(Service.Level.APP)
class PackageSearchApiClientService {
    val client = PackageSearchApiClient(
        endpoints = IntelliJApplication.PackageSearchApiEndpointsService.endpoints,
    )
}