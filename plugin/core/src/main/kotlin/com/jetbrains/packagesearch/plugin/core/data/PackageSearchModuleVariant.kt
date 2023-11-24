package com.jetbrains.packagesearch.plugin.core.data

import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiPackageVersion
import org.jetbrains.packagesearch.api.v3.search.PackagesType

interface PackageSearchModuleVariant : PackageSearchDependencyManager {

    val name: String
    val variantTerminology: PackageSearchModule.WithVariants.Terminology?
    val declaredDependencies: List<PackageSearchDeclaredPackage.WithVariant>
    val attributes: List<Attribute>
    val compatiblePackageTypes: List<PackagesType>
    val isPrimary: Boolean
    val dependencyMustHaveAScope: Boolean
    val availableScopes: List<String>
    val defaultScope: String?


    @Serializable
    sealed interface Attribute {

        val value: String

        @JvmInline
        @Serializable
        value class StringAttribute(override val value: String) : Attribute

        data class NestedAttribute(override val value: String, val children: List<Attribute>) : Attribute
    }

    fun isCompatible(dependency: ApiPackage, version: ApiPackageVersion): Boolean

}
