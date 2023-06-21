package org.jetbrains.packagesearch.plugin.maven

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import org.jetbrains.packagesearch.plugin.core.data.WithIcon.Icons

@Serializable
@SerialName("maven-version")
data class PackageSearchDeclaredMavenPackage(
    override val id: String,
    override val declaredVersion: NormalizedVersion,
    override val latestStableVersion: NormalizedVersion,
    override val latestVersion: NormalizedVersion,
    override val remoteInfo: ApiPackage?,
    override val declarationIndexes: DependencyDeclarationIndexes?,
    val groupId: String,
    val artifactId: String,
    val scope: String? = null
) : PackageSearchDeclaredPackage {
    override val icon
        get() = Icons.MAVEN
}