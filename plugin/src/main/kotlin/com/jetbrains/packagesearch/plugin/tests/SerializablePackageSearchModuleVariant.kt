package com.jetbrains.packagesearch.plugin.tests

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.search.PackagesType

@Serializable
data class SerializablePackageSearchModuleVariant(
    val name: String,
    val variantTerminology: PackageSearchModule.WithVariants.Terminology?,
    val declaredDependencies: List<SerializablePackageSearchDeclaredPackage>,
    val attributes: List<PackageSearchModuleVariant.Attribute>,
    val compatiblePackageTypes: List<PackagesType>,
    val isPrimary: Boolean,
    val dependencyMustHaveAScope: Boolean,
    val availableScopes: List<String>,
    val defaultScope: String?,
)