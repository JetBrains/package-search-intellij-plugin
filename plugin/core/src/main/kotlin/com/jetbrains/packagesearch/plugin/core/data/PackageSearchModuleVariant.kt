package com.jetbrains.packagesearch.plugin.core.data

import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiPackageVersion
import org.jetbrains.packagesearch.api.v3.search.PackagesType

interface PackageSearchModuleVariant : PackageInstallDataProvider {

    data class Terminology(val singular: String, val plural: String) {
        fun getForCardinality(count: Int) = if (count > 1) plural else singular

        companion object {
            val DEFAULT = Terminology("variant", "variants")
        }
    }

    val name: String
    val variantTerminology: Terminology?
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

fun PackageSearchModuleVariant.Attribute.NestedAttribute.flatten() = buildSet {
    val queue = mutableListOf(this@flatten)
    while (queue.isNotEmpty()) {
        val next = queue.removeFirst()
        addAll(next.children.filterIsInstance<PackageSearchModuleVariant.Attribute.StringAttribute>().map { it.value })
        queue.addAll(next.children.filterIsInstance<PackageSearchModuleVariant.Attribute.NestedAttribute>())
    }
}
