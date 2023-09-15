package com.jetbrains.packagesearch.plugin.maven

import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredMavenPackage
import com.jetbrains.packagesearch.plugin.core.data.IconProvider.Icons
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes

@Serializable
@SerialName("maven")
data class PackageSearchDeclaredBaseMavenPackage(
    override val id: String,
    override val declaredVersion: NormalizedVersion,
    override val latestStableVersion: NormalizedVersion,
    override val latestVersion: NormalizedVersion,
    override val remoteInfo: ApiMavenPackage?,
    override val declarationIndexes: DependencyDeclarationIndexes,
    override val groupId: String,
    override val artifactId: String,
    override val scope: String? = null,
    override val icon: IconProvider.Icon
) : PackageSearchDeclaredMavenPackage {

    override fun getUpdateData(newVersion: String?, newScope: String?) =
        MavenUpdatePackageData(
            installedPackage = this,
            newVersion = newVersion ?: declaredVersion.versionName,
            newScope = newScope
        )

    override fun getRemoveData() =
        MavenRemovePackageData(this)

}