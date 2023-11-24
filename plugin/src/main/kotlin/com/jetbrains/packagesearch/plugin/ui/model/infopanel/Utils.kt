package com.jetbrains.packagesearch.plugin.ui.model.infopanel

import com.jetbrains.packagesearch.plugin.PackageSearchBundle.message
import org.jetbrains.packagesearch.api.v3.ApiGitHub
import org.jetbrains.packagesearch.api.v3.ApiScm
import org.jetbrains.packagesearch.api.v3.LicenseFile
import org.jetbrains.packagesearch.api.v3.Licenses

internal fun ApiScm.asInfoPanelScm() = when (this) {
    is ApiGitHub -> InfoPanelContent.PackageInfo.Scm.GitHub(
        name = message("packagesearch.ui.toolwindow.packages.details.info.scm.github"),
        url = url,
        stars = stars ?: 0
    )
}

internal fun Licenses<*>.asInfoPanelLicenseList() = buildList {
    mainLicense.toInfoPanelLicense()?.let { add(it) }
    otherLicenses.forEach {
        it.toInfoPanelLicense()?.let { add(it) }
    }
}

internal fun LicenseFile.toInfoPanelLicense(): InfoPanelContent.PackageInfo.License? {
    val name = name ?: url ?: return null
    return InfoPanelContent.PackageInfo.License(name, url)
}