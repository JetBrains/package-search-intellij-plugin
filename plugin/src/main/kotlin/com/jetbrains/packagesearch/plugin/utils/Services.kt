package com.jetbrains.packagesearch.plugin.utils

import com.intellij.openapi.application.Application
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.services.PackageSearchApiClientService
import com.jetbrains.packagesearch.plugin.services.PackageSearchApiEndpointsService
import com.jetbrains.packagesearch.plugin.services.PackageSearchApplicationCachesService
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService
import org.jetbrains.packagesearch.plugin.services.PackageSearchUIStateService

val Application.PackageSearchApiEndpointsService
    get() = service<PackageSearchApiEndpointsService>()

val Application.PackageSearchApiClientService
    get() = service<PackageSearchApiClientService>()

val Application.PackageSearchApplicationCachesService
    get() = service<PackageSearchApplicationCachesService>()

val Project.PackageSearchProjectService
    get() = service<PackageSearchProjectService>()

val Project.PackageSearchUIStateService
    get() = service<PackageSearchUIStateService>()