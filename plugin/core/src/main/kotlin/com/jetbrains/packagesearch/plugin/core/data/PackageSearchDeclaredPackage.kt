package com.jetbrains.packagesearch.plugin.core.data

import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion


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
