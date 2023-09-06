package com.jetbrains.packagesearch.plugin.core.data

import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.PackagesType

interface PackageSearchModuleVariant : PackageInstallDataProvider {

    data class Terminology(val singular: String, val plural: String)

    val name: String
    val variantTerminology: Terminology?
    val declaredDependencies: List<PackageSearchDeclaredPackage.WithVariant>
    val attributes: List<String>
    val compatiblePackageTypes: List<PackagesType>
    val isPrimary: Boolean


    fun isCompatible(dependency: ApiPackage, version: String): Boolean

}