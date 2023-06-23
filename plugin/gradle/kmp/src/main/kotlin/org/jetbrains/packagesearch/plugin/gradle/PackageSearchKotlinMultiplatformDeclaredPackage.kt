package org.jetbrains.packagesearch.plugin.gradle

import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import org.jetbrains.packagesearch.plugin.core.data.WithIcon
import org.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes

interface PackageSearchKotlinMultiplatformDeclaredPackage : PackageSearchDeclaredPackage {
    data class Maven(
        override val id: String,
        override val declaredVersion: NormalizedVersion,
        override val latestStableVersion: NormalizedVersion,
        override val latestVersion: NormalizedVersion,
        override val remoteInfo: ApiPackage?,
        override val declarationIndexes: DependencyDeclarationIndexes?,
        val groupId: String,
        val artifactId: String,
        val configuration: String
    ) : PackageSearchKotlinMultiplatformDeclaredPackage {
        override val icon: WithIcon.PathSourceType
            get() = WithIcon.Icons.MAVEN
    }

    data class Cocoapods(
        override val id: String,
        override val declaredVersion: NormalizedVersion,
        override val latestStableVersion: NormalizedVersion,
        override val latestVersion: NormalizedVersion,
        override val remoteInfo: ApiPackage?,
        override val declarationIndexes: DependencyDeclarationIndexes?,
        val name: String,
    ) : PackageSearchKotlinMultiplatformDeclaredPackage {
        override val icon: WithIcon.PathSourceType
            get() = WithIcon.Icons.COCOAPODS
    }

    data class Npm(
        override val id: String,
        override val declaredVersion: NormalizedVersion,
        override val latestStableVersion: NormalizedVersion,
        override val latestVersion: NormalizedVersion,
        override val remoteInfo: ApiPackage?,
        override val declarationIndexes: DependencyDeclarationIndexes?,
        val name: String
    ) : PackageSearchKotlinMultiplatformDeclaredPackage {
        override val icon: WithIcon.PathSourceType
            get() = WithIcon.Icons.NPM
    }
}