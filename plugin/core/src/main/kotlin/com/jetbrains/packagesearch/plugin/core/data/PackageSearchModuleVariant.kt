package com.jetbrains.packagesearch.plugin.core.data

import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.PackagesType

interface PackageSearchModuleVariant : PackageInstallDataProvider {
    val name: String
    val variantTerminology: String?
    val declaredDependencies: List<PackageSearchDeclaredPackage.WithVariant>
    val attributes: List<Attributes>
    val compatiblePackageTypes: List<PackagesType>

    @Serializable
    data class Attributes(val name: String, val children: List<Attributes>)

    fun isCompatible(dependency: ApiPackage, version: String): Boolean

}