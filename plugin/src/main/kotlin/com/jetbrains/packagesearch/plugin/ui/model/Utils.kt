package com.jetbrains.packagesearch.plugin.ui.model

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.utils.flow
import com.jetbrains.packagesearch.plugin.core.utils.withInitialValue
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

internal fun PackageSearchDeclaredPackage.hasUpdates(onlyStable: Boolean): Boolean {
    val declared = declaredVersion ?: return false
    val latestVersion = getLatestVersion(onlyStable) ?: return false
    return declared < latestVersion
}

internal fun PackageSearchDeclaredPackage.getLatestVersion(onlyStable: Boolean): NormalizedVersion? {
    val declaredVersion = declaredVersion ?: return null
    return when {
        onlyStable -> remoteInfo?.versions?.latestStable?.normalized?.takeIf { it > declaredVersion }
        else -> remoteInfo?.versions?.latest?.normalized?.takeIf { it > declaredVersion }
    }
}