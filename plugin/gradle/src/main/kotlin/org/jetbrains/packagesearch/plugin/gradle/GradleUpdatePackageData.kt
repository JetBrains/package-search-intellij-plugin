package org.jetbrains.packagesearch.plugin.gradle

import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.plugin.core.data.*

data class GradleUpdatePackageData(
    override val installedPackage: PackageSearchDeclaredMavenPackage,
    override val newVersion: String?,
    val newConfiguration: String
) : UpdatePackageData

data class GradleInstallPackageData(
    override val apiPackage: ApiMavenPackage,
    override val selectedVersion: String,
    val selectedConfiguration: String,
) : InstallPackageData

data class GradleRemovePackageData(
    override val declaredPackage: PackageSearchDeclaredMavenPackage,
) : RemovePackageData