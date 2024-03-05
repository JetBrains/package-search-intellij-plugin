package com.jetbrains.packagesearch.plugin.core.data

import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

interface PackageSearchDeclaredMavenPackage : PackageSearchDeclaredPackage {
    val groupId: String
    val artifactId: String
    override val remoteInfo: ApiMavenPackage?

    override val coordinates: String
        get() = "$groupId:$artifactId"

    override val displayName: String
        get() = remoteInfo?.name ?: artifactId
}

@Serializable
@SerialName("maven")
data class PackageSearchDeclaredBaseMavenPackage(
    override val declaredVersion: NormalizedVersion?,
    override val remoteInfo: ApiMavenPackage?,
    override val declarationIndexes: DependencyDeclarationIndexes,
    override val groupId: String,
    override val artifactId: String,
    override val declaredScope: String? = null,
) : PackageSearchDeclaredMavenPackage {
    override val id: String
        get() = "maven:$groupId:$artifactId"

    override val icon: IconProvider.Icon
        get() = IconProvider.Icons.MAVEN
}

data class MavenDependencyModel(
    val groupId: String,
    val artifactId: String,
    val version: String?,
    val scope: String?,
    val indexes: DependencyDeclarationIndexes,
) {

    val packageId
        get() = "maven:$groupId:$artifactId"

}