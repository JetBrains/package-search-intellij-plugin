package org.jetbrains.packagesearch.plugin.core.data

import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes

interface PackageSearchDeclaredPackage : WithIcon {
    val id: String
    val displayName: String
    val declaredVersion: NormalizedVersion
    val latestStableVersion: NormalizedVersion
    val latestVersion: NormalizedVersion
    val remoteInfo: ApiPackage?
    val declarationIndexes: DependencyDeclarationIndexes?
    val scope: String?

    fun getUpdateData(newVersion: String?, newScope: String?): UpdatePackageData
    fun getDeleteData(): RemovePackageData
}

interface PackageSearchDeclaredMavenPackage : PackageSearchDeclaredPackage {
    val groupId: String
    val artifactId: String
    override val remoteInfo: ApiMavenPackage?

    override val displayName: String
        get() = remoteInfo?.name ?: "$groupId:$artifactId"
}