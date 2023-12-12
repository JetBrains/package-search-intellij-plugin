package com.jetbrains.packagesearch.plugin.maven

import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredMavenPackage
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

@Serializable
@SerialName("maven")
data class PackageSearchDeclaredJpsPackage(
    override val id: String,
    override val declaredVersion: NormalizedVersion?,
    override val remoteInfo: ApiMavenPackage?,
    override val declarationIndexes: DependencyDeclarationIndexes,
    override val groupId: String,
    override val artifactId: String,
    override val declaredScope: String,
    override val icon: IconProvider.Icon,
) : PackageSearchDeclaredMavenPackage