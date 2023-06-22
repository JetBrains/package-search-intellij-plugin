package org.jetbrains.packagesearch.plugin.gradle

import org.jetbrains.packagesearch.api.v3.ApiGradlePackage
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.plugin.core.data.InstallPackageData
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import org.jetbrains.packagesearch.plugin.core.data.RemovePackageData
import org.jetbrains.packagesearch.plugin.core.data.UpdatePackageData

data class GradleUpdatePackageData(
    override val installedPackage: PackageSearchGradleDeclaredPackage,
    override val newVersion: String?,
    val configuration: String
) : UpdatePackageData

data class GradleInstallPackageData(
    override val apiPackage: ApiMavenPackage,
    override val selectedVersion: String,
    val configuration: String,
) : InstallPackageData

data class GradleRemovePackageData(
    override val declaredPackage: PackageSearchGradleDeclaredPackage,
    val configuration: String
) : RemovePackageData