package com.jetbrains.packagesearch.plugin.utils

import com.intellij.openapi.application.Application
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.services.PackageSearchApplicationCachesService
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService


val Application.PackageSearchApplicationCachesService
    get() = service<PackageSearchApplicationCachesService>()

val Project.PackageSearchProjectService
    get() = service<PackageSearchProjectService>()
