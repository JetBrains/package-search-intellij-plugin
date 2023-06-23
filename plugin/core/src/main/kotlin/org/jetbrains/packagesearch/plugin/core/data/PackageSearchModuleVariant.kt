package org.jetbrains.packagesearch.plugin.core.data

import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

interface PackageSearchModuleVariant : WithIcon {
    val name: String
    val variantTerminology: String?
    val declaredDependencies: List<PackageSearchDeclaredPackage>
    val badges: List<Badge>
    val compatiblePackageTypes: List<PackagesType>

    data class Badge(val name: String, val isAvailable: String, val children: List<Badge>)

    fun isCompatible(dependency: ApiPackage, version: NormalizedVersion): Boolean
}