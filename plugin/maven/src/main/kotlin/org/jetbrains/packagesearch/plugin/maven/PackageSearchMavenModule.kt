@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.maven

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import org.jetbrains.packagesearch.plugin.core.data.*
import org.jetbrains.packagesearch.plugin.core.data.WithIcon.Icons

data class MavenUpdatePackageData(
    override val installedPackage: PackageSearchDeclaredBaseMavenPackage,
    override val newVersion: String?,
    val newScope: String? = null
) : UpdatePackageData

data class MavenInstallPackageData(
    override val apiPackage: ApiMavenPackage,
    override val selectedVersion: String,
    val selectedScope: String? = null
) : InstallPackageData

data class MavenRemovePackageData(
    override val declaredPackage: PackageSearchDeclaredBaseMavenPackage
) : RemovePackageData

@Serializable
@SerialName("maven")
data class PackageSearchMavenModule(
    override val name: String,
    override val projectDirPath: String,
    override val buildFilePath: String,
    override val declaredKnownRepositories: Map<String, ApiRepository>,
    override val declaredDependencies: List<PackageSearchDeclaredBaseMavenPackage>,
    override val defaultScope: String? = null,
    override val availableScopes: List<String>,
    override val compatiblePackageTypes: List<PackagesType>
) : PackageSearchModule.Base {

    override val icon
        get() = Icons.MAVEN

}