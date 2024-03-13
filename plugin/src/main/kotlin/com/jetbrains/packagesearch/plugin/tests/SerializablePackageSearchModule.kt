package com.jetbrains.packagesearch.plugin.tests

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredRepository
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.search.PackagesType

@Serializable
sealed interface SerializablePackageSearchModule {

    val name: String
    val identity: SerializableIdentity
    val declaredRepositories: List<SerializablePackageSearchDeclaredRepository>

    val compatiblePackageTypes: List<PackagesType>
    val dependencyMustHaveAScope: Boolean

    @Serializable
    data class Base(
        override val name: String,
        override val identity: SerializableIdentity,
        override val declaredRepositories: List<SerializablePackageSearchDeclaredRepository>,
        override val compatiblePackageTypes: List<PackagesType>,
        override val dependencyMustHaveAScope: Boolean,
        val declaredDependencies: List<SerializablePackageSearchDeclaredPackage>,
        val availableScopes: List<String>,
        val defaultScope: String?,
    ) : SerializablePackageSearchModule

    @Serializable
    data class WithVariants(
        override val name: String,
        override val identity: SerializableIdentity,
        override val declaredRepositories: List<SerializablePackageSearchDeclaredRepository>,
        override val compatiblePackageTypes: List<PackagesType>,
        override val dependencyMustHaveAScope: Boolean,
        val variants: Map<String, SerializablePackageSearchModuleVariant>,
        val variantTerminology: PackageSearchModule.WithVariants.Terminology,
        val mainVariantName: String,
    ) : SerializablePackageSearchModule

}
