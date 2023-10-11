package com.jetbrains.packagesearch.plugin.core.data

import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion


interface PackageSearchDeclaredPackage : IconProvider {

    interface WithVariant : PackageSearchDeclaredPackage {
        val variantName: String
    }

    val id: String
    val displayName: String
    val coordinates: String
    val declaredVersion: NormalizedVersion
    val latestStableVersion: NormalizedVersion
    val latestVersion: NormalizedVersion
    val remoteInfo: ApiPackage?
    val declarationIndexes: DependencyDeclarationIndexes
    val scope: String?

    fun getUpdateData(newVersion: String?, newScope: String? = scope): UpdatePackageData
    fun getRemoveData(): RemovePackageData

}

interface PackageSearchDeclaredMavenPackage : PackageSearchDeclaredPackage {
    val groupId: String
    val artifactId: String
    override val remoteInfo: ApiMavenPackage?

    override val coordinates: String
        get() = "$groupId:$artifactId"

    override val displayName: String
        get() = remoteInfo?.name ?: artifactId
}


/**
 * Checks if the declared package can be upgraded.
 *
 * @return theLatestApiVersion if the package can be upgraded, null otherwise.
 */
val PackageSearchDeclaredPackage.latestStableOrNull
    get() = remoteInfo?.let {
        it.versions.latestStable
            ?.takeIf { it.normalized > declaredVersion }
    }

fun ApiPackage.getAvailableVersions(onlyStable: Boolean = false) =
    versions
        .all
        .values
        .map { it.normalized }
        .filter { if (onlyStable) it.isStable else true }