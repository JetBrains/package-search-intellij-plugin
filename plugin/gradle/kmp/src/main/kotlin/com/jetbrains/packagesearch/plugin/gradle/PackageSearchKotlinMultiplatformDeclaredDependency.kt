package com.jetbrains.packagesearch.plugin.gradle

import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredMavenPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes

@Serializable
sealed class PackageSearchKotlinMultiplatformDeclaredDependency : PackageSearchDeclaredPackage.WithVariant {

    override fun getUpdateData(newVersion: String?, newScope: String?) =
        KotlinMultiplatformUpdatePackageData(
            installedPackage = this,
            newVersion = newVersion,
            newScope = newScope,
            sourceSetName = variantName
        )

    override fun getRemoveData() = KotlinMultiplatformRemovePackageData(
        declaredPackage = this,
        variantName = variantName
    )

    @Serializable
    data class Maven(
        override val id: String,
        override val declaredVersion: NormalizedVersion,
        override val latestStableVersion: NormalizedVersion,
        override val latestVersion: NormalizedVersion,
        override val remoteInfo: ApiMavenPackage?,
        override val declarationIndexes: DependencyDeclarationIndexes,
        override val groupId: String,
        override val artifactId: String,
        override val variantName: String,
        val configuration: String,
    ) : PackageSearchKotlinMultiplatformDeclaredDependency(), PackageSearchDeclaredMavenPackage {
        override val lightIconPath: String
            get() = IconProvider.Icons.MAVEN
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
        override val declarationIndexes: DependencyDeclarationIndexes,
        override val variantName: String,
        override val displayName: String,
        val name: String
    ) : PackageSearchKotlinMultiplatformDeclaredDependency() {
        override val lightIconPath: String
            get() = IconProvider.Icons.COCOAPODS

        override val scope: String? = null

        override val coordinates: String
            get() = displayName
    }

    @Serializable
    data class Npm(
        override val id: String,
        override val declaredVersion: NormalizedVersion,
        override val latestStableVersion: NormalizedVersion,
        override val latestVersion: NormalizedVersion,
        override val remoteInfo: ApiPackage?,
        override val declarationIndexes: DependencyDeclarationIndexes,
        override val variantName: String,
        val configuration: String,
        override val displayName: String,
        val name: String
    ) : PackageSearchKotlinMultiplatformDeclaredDependency() {
        override val lightIconPath: String
            get() = IconProvider.Icons.NPM
        override val scope: String
            get() = configuration
        override val coordinates: String
            get() = name
    }

}