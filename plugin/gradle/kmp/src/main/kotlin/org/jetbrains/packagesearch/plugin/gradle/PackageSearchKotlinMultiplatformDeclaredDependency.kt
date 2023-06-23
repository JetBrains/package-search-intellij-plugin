package org.jetbrains.packagesearch.plugin.gradle

import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredMavenPackage
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import org.jetbrains.packagesearch.plugin.core.data.WithIcon
import org.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes

@Serializable
sealed interface PackageSearchKotlinMultiplatformDeclaredDependency : PackageSearchDeclaredPackage {

    sealed interface ForSourceSet : PackageSearchKotlinMultiplatformDeclaredDependency {
        val configuration: String
    }

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
        override val configuration: String
    ) : ForSourceSet, PackageSearchDeclaredMavenPackage {
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
        val name: String,
    ) : PackageSearchKotlinMultiplatformDeclaredDependency {
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
        override val configuration: String,
        val name: String
    ) : ForSourceSet {
        override val icon: WithIcon.PathSourceType
            get() = WithIcon.Icons.NPM
    }
}