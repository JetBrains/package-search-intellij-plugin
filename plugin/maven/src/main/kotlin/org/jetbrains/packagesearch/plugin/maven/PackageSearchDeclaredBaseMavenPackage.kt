package org.jetbrains.packagesearch.plugin.maven

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredMavenPackage
import org.jetbrains.packagesearch.plugin.core.data.WithIcon.Icons
import org.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes

@Serializable
@SerialName("maven-version")
data class PackageSearchDeclaredBaseMavenPackage(
    override val id: String,
    override val declaredVersion: NormalizedVersion,
    override val latestStableVersion: NormalizedVersion,
    override val latestVersion: NormalizedVersion,
    override val remoteInfo: ApiPackage?,
    override val declarationIndexes: DependencyDeclarationIndexes?,
    override val groupId: String,
    override val artifactId: String,
    override val scope: String? = null
) : PackageSearchDeclaredMavenPackage {
    override val icon
        get() = Icons.MAVEN
}