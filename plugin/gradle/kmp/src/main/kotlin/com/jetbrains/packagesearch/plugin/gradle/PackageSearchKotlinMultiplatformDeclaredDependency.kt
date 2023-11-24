package com.jetbrains.packagesearch.plugin.gradle

import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredMavenPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

@Serializable
sealed class PackageSearchKotlinMultiplatformDeclaredDependency : PackageSearchDeclaredPackage.WithVariant {

    @Serializable
    data class Maven(
        override val id: String,
        override val declaredVersion: NormalizedVersion?,
        override val remoteInfo: ApiMavenPackage?,
        override val declarationIndexes: DependencyDeclarationIndexes,
        override val groupId: String,
        override val artifactId: String,
        override val variantName: String,
        override val icon: IconProvider.Icon,
        val configuration: String,
    ) : PackageSearchKotlinMultiplatformDeclaredDependency(), PackageSearchDeclaredMavenPackage {
        override val declaredScope
            get() = configuration

    }

    @Serializable
    data class Cocoapods(
        override val id: String,
        override val declaredVersion: NormalizedVersion,
        override val remoteInfo: ApiPackage?,
        override val declarationIndexes: DependencyDeclarationIndexes,
        override val variantName: String,
        override val displayName: String,
        val name: String,
    ) : PackageSearchKotlinMultiplatformDeclaredDependency() {
        override val icon
            get() = IconProvider.Icons.COCOAPODS

        override val declaredScope: String? = null

        override val coordinates: String
            get() = displayName
    }

    @Serializable
    data class Npm(
        override val id: String,
        override val declaredVersion: NormalizedVersion,
        override val remoteInfo: ApiPackage?,
        override val declarationIndexes: DependencyDeclarationIndexes,
        override val variantName: String,
        val configuration: String,
        override val displayName: String,
        val name: String,
    ) : PackageSearchKotlinMultiplatformDeclaredDependency() {
        override val icon
            get() = IconProvider.Icons.NPM

        override val declaredScope: String
            get() = configuration
        override val coordinates: String
            get() = name
    }

}