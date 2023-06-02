@file:Suppress("FunctionName")

package org.jetbrains.packagesearch.plugin.core.data

import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType

/**
 * Package Search representation of a module.
 **/
@Serializable
sealed interface PackageSearchModule : WithIcon {

    val name: String
    val projectDirPath: String
    val buildFilePath: String?
    val declaredKnownRepositories: Map<String, ApiRepository>
    val defaultScope: String?

    interface WithVariants : PackageSearchModule {
        val variantTerminology: String
        val declaredDependencies: List<PackageSearchModuleVariant>
    }

    interface Base : PackageSearchModule {
        val compatiblePackageTypes: List<PackagesType>
        val declaredDependencies: List<PackageSearchDeclaredDependency>
    }

}

