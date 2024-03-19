package com.jetbrains.packagesearch.plugin.gradle

import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredMavenPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredRepository
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

@Serializable
@SerialName("gradle-version")
data class PackageSearchGradleDeclaredPackage(
    override val id: String,
    override val declaredVersion: NormalizedVersion?,
    override val remoteInfo: ApiMavenPackage?,
    override val declarationIndexes: DependencyDeclarationIndexes,
    override val icon: IconProvider.Icon,
    val module: String,
    val name: String,
    val configuration: String
) : PackageSearchDeclaredMavenPackage {
    override val groupId: String
        get() = module
    override val artifactId: String
        get() = name
    override val declaredScope: String
        get() = configuration
}

@Serializable
@SerialName("gradle-repository")
data class PackageSearchGradleDeclaredRepository(
    override val url: String,
    override val remoteInfo: ApiMavenRepository?,
    override val name: String?,
) : PackageSearchDeclaredRepository