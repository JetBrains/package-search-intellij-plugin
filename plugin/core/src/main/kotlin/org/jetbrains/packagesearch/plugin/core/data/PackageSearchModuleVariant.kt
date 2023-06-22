package org.jetbrains.packagesearch.plugin.core.data

import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.PackagesType

interface PackageSearchModuleVariant : WithIcon {
    val name: String
    val variantTerminology: String?
    val declaredDependencies: List<PackageSearchDeclaredPackage>
    val badges: List<Badge>
    val compatiblePackageTypes: List<PackagesType>
    val defaultScope: String?

    data class Badge(val name: String, val isAvailable: String, val children: List<Badge>)

    fun isCompatible(dependency: ApiPackage): Boolean
}