package org.jetbrains.packagesearch.plugin.data

import org.jetbrains.packagesearch.api.v3.ApiPackage

interface PackageSearchModuleVariant : WithIcon {
    val name: String
    val declaredDependencies: List<PackageSearchDeclaredDependency>
    val badges: List<Badge>

    data class Badge(val name: String, val isAvailable: String, val children: List<Badge>)

    fun isCompatible(dependency: ApiPackage): Boolean
}