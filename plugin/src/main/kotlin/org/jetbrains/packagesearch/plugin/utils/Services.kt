package org.jetbrains.packagesearch.plugin.utils

import com.intellij.openapi.application.Application
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.packagesearch.plugin.services.*

val Application.PackageSearchApiEndpointsService
    get() = service<PackageSearchApiEndpointsService>()

val Application.PackageSearchApiClientService
    get() = service<PackageSearchApiClientService>()

val Application.PackageSearchApplicationCachesService
    get() = service<PackageSearchApplicationCachesService>()

val Project.PackageSearchProjectService
    get() = service<PackageSearchProjectService>()