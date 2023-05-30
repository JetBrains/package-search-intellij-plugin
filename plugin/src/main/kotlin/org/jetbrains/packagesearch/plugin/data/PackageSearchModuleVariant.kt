package org.jetbrains.packagesearch.plugin.data

import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.PackagesType

interface PackageSearchModuleVariant : WithIcon {
    val name: String
    val declaredDependencies: List<PackageSearchDeclaredDependency>
    val badges: List<Badge>
    val compatiblePackageTypes: List<PackagesType>

    data class Badge(val name: String, val isAvailable: String, val children: List<Badge>)

    fun isCompatible(dependency: ApiPackage): Boolean
}