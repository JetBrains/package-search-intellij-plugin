package com.jetbrains.packagesearch.plugin.utils

import com.intellij.openapi.application.Application
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.fus.PackageSearchFUSEvent
import com.jetbrains.packagesearch.plugin.services.PackageSearchFUSService
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.services.PackageSearchSettingsService
import org.jetbrains.idea.packagesearch.api.PackageSearchApiClientService


val Application.PackageSearchApiClientService
    get() = service< PackageSearchApiClientService>()

val Project.PackageSearchProjectService
    get() = service<PackageSearchProjectService>()

val Project.PackageSearchSettingsService
    get() = service<PackageSearchSettingsService>()

val Application.PackageSearchFUSService
    get() = service<PackageSearchFUSService>()

val PackageSearchLogger: PluginLogger
    get() = IntelliJApplication.service<IntelliJLogger>()

internal fun logFUSEvent(event: PackageSearchFUSEvent) =
    IntelliJApplication.PackageSearchFUSService.logEvent(event)
