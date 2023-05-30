package org.jetbrains.packagesearch.plugin.maven

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.extensions.DependencyDeclarationIndexes
import org.jetbrains.packagesearch.plugin.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.data.WithIcon
import org.jetbrains.packagesearch.plugin.data.WithIcon.PathSourceType.ClasspathResources

@Serializable
@SerialName("maven-version")
data class PackageSearchDeclaredMavenDependency(
    override val id: String,
    override val declaredVersion: NormalizedVersion,
    override val latestStableVersion: NormalizedVersion,
    override val latestVersion: NormalizedVersion,
    override val remoteInfo: ApiPackage?,
    override val declarationIndexes: DependencyDeclarationIndexes?,
    val groupId: String,
    val artifactId: String,
    val scope: String? = null
) : PackageSearchDeclaredDependency {
    override val icon
        get() = ClasspathResources("icons/maven.svg")
}