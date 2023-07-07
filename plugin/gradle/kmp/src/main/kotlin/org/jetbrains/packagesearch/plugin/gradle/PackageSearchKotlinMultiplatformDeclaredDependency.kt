package org.jetbrains.packagesearch.plugin.gradle

import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredMavenPackage
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import org.jetbrains.packagesearch.plugin.core.data.WithIcon
import org.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes

@Serializable
sealed class PackageSearchKotlinMultiplatformDeclaredDependency : PackageSearchDeclaredPackage {

    abstract val variantName: String

    override fun getUpdateData(newVersion: String?, newScope: String?) =
        KotlinMultiplatformUpdatePackageData(
            installedPackage = this,
            newVersion = newVersion,
            newScope = newScope,
            sourceSetName = variantName
        )

    override fun getDeleteData() = KotlinMultiplatformRemovePackageData(
        declaredPackage = this,
        variantName = variantName
    )

    @Serializable
    data class Maven(
        override val id: String,
        override val declaredVersion: NormalizedVersion,
        override val latestStableVersion: NormalizedVersion,
        override val latestVersion: NormalizedVersion,
        override val remoteInfo: ApiPackage?,
        override val declarationIndexes: DependencyDeclarationIndexes?,
        override val groupId: String,
        override val artifactId: String,
        override val variantName: String,
        val configuration: String,
    ) : PackageSearchKotlinMultiplatformDeclaredDependency(), PackageSearchDeclaredMavenPackage {
        override val icon: WithIcon.PathSourceType
            get() = WithIcon.Icons.MAVEN
        override val scope
            get() = configuration
    }

    @Serializable
    data class Cocoapods(
        override val id: String,
        override val declaredVersion: NormalizedVersion,
        override val latestStableVersion: NormalizedVersion,
        override val latestVersion: NormalizedVersion,
        override val remoteInfo: ApiPackage?,
        override val declarationIndexes: DependencyDeclarationIndexes?,
        override val variantName: String,
        val name: String,
    ) : PackageSearchKotlinMultiplatformDeclaredDependency() {
        override val icon: WithIcon.PathSourceType
            get() = WithIcon.Icons.COCOAPODS
    }

    @Serializable
    data class Npm(
        override val id: String,
        override val declaredVersion: NormalizedVersion,
        override val latestStableVersion: NormalizedVersion,
        override val latestVersion: NormalizedVersion,
        override val remoteInfo: ApiPackage?,
        override val declarationIndexes: DependencyDeclarationIndexes?,
        override val variantName: String,
        val configuration: String,
        val name: String
    ) : PackageSearchKotlinMultiplatformDeclaredDependency() {
        override val icon: WithIcon.PathSourceType
            get() = WithIcon.Icons.NPM
    }
}