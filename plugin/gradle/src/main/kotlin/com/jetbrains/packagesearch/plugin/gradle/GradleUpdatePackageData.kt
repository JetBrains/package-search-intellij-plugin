
package com.jetbrains.packagesearch.plugin.gradle

import com.jetbrains.packagesearch.plugin.core.data.InstallPackageData
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredMavenPackage
import com.jetbrains.packagesearch.plugin.core.data.RemovePackageData
import com.jetbrains.packagesearch.plugin.core.data.UpdatePackageData
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackageVersion

data class GradleUpdatePackageData(
    override val installedPackage: PackageSearchDeclaredMavenPackage,
    override val newVersion: String?,
    override val newScope: String?
) : UpdatePackageData

data class GradleInstallPackageData(
    override val apiPackage: ApiMavenPackage,
    override val selectedVersion: ApiPackageVersion,
    val selectedConfiguration: String,
) : InstallPackageData

data class GradleRemovePackageData(
    override val declaredPackage: PackageSearchDeclaredMavenPackage,
) : RemovePackageData