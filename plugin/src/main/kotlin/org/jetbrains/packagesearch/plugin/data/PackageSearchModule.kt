@file:Suppress("FunctionName")

package org.jetbrains.packagesearch.plugin.data

import org.jetbrains.packagesearch.api.v3.ApiRepository

/**
 * Package Search representation of a module.
 **/
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
        val declaredDependencies: List<PackageSearchDeclaredDependency>
    }

}

