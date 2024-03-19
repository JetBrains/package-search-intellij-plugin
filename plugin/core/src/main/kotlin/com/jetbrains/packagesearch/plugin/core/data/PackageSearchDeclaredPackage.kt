package com.jetbrains.packagesearch.plugin.core.data

import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage.GradleVersion.ApiVariant
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion


interface PackageSearchDeclaredRepository {
    val url: String
    val name: String?
    val remoteInfo: ApiRepository?
}

interface PackageSearchDeclaredPackage : IconProvider {

    interface WithVariant : PackageSearchDeclaredPackage {
        val variantName: String
    }

    val id: String
    val displayName: String
    val coordinates: String
    val declaredVersion: NormalizedVersion?
    val remoteInfo: ApiPackage?
    val declarationIndexes: DependencyDeclarationIndexes
    val declaredScope: String?

}


fun PackageSearchDeclaredPackage.listKMPAttributesNames(onlyStable: Boolean): Set<String> {
    val version = getLatestVersion(onlyStable) as? ApiMavenPackage.GradleVersion ?: return emptySet()
    return version.listKMPAttributesNames()
}

fun ApiPackage.listKMPAttributesNames(onlyStable: Boolean): Set<String> {
    val version = getLatestVersion(onlyStable) as? ApiMavenPackage.GradleVersion ?: return emptySet()
    return version.listKMPAttributesNames()
}

fun ApiMavenPackage.GradleVersion.listKMPAttributesNames(): Set<String> = buildSet {
    for (apiVariant in variants) {
        val variantAttributes =
            apiVariant.attributes["org.jetbrains.kotlin.platform.type"] as? ApiVariant.Attribute.ExactMatch
                ?: continue
        when (variantAttributes.value) {
            "native" ->
                when (val target = apiVariant.attributes["org.jetbrains.kotlin.native.target"]) {
                    is ApiVariant.Attribute.ExactMatch -> add(target.value)
                    else -> continue
                }

            "common" -> continue
            else -> add(variantAttributes.value)
        }
    }
}

fun PackageSearchDeclaredPackage.getLatestVersion(onlyStable: Boolean) =
    if (onlyStable) remoteInfo?.versions?.latestStable else remoteInfo?.versions?.latest

fun ApiPackage.getLatestVersion(onlyStable: Boolean) =
    if (onlyStable) versions.latestStable else versions.latest

