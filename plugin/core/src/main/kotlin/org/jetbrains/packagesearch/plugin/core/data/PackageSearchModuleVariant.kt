package org.jetbrains.packagesearch.plugin.core.data

import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

interface PackageSearchModuleVariant {
    val name: String
    val variantTerminology: String?
    val declaredDependencies: List<PackageSearchDeclaredPackage>
    val badges: List<Badge>
    val compatiblePackageTypes: List<PackagesType>

    @Serializable
    data class Badge(val name: String, val isAvailable: String, val children: List<Badge>)

    fun isCompatible(dependency: ApiPackage, version: String): Boolean
}