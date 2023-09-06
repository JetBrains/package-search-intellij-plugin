package com.jetbrains.packagesearch.plugin.ui.models

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes

sealed interface SearchData {

    sealed interface Results {
        data object Empty : Results
    }

    data object Empty : SearchData

    data class SingleBaseModule(
        val searchParameters: SearchPackagesRequest,
        val module: PackageSearchModule.Base
    ) : SearchData {

        fun withResults(results: List<ApiPackage>) =
            Results(this, results)

        data class Results(
            val searchData: SingleBaseModule,
            val results: List<ApiPackage>
        ) : SearchData.Results
    }

    data class SingleWithVariantsModule(
        val searchForVariant: List<SearchForVariants>,
        val module: PackageSearchModule.WithVariants
    ) : SearchData {

        fun withResults(results: List<SearchForVariants.Results>) =
            Results(module, results)

        data class Results(
            val module: PackageSearchModule.WithVariants,
            val results: List<SearchForVariants.Results>
        ) : SearchData.Results

        data class SearchForVariants(
            val searchParameters: SearchPackagesRequest,
            val compatibleVariants: List<PackageSearchModuleVariant>
        ) {

            fun withResults(results: List<ApiPackage>) =
                Results(this, results)

            data class Results(
                val searchData: SearchForVariants,
                val results: List<ApiPackage>
            )
        }
    }

    data class MultipleModules(
        val searchParameters: SearchPackagesRequest,
        val modules: List<PackageSearchModule>
    ) : SearchData {

        fun withResults(results: List<ApiPackage>) =
            Results(this, results)

        data class Results(
            val searchData: MultipleModules,
            val results: List<ApiPackage>
        ) : SearchData.Results
    }
}

internal fun buildSearchData(
    selectedModules: List<PackageSearchModule>,
    searchQuery: String
) = when {
    selectedModules.isEmpty() || searchQuery.isEmpty() -> SearchData.Empty
    selectedModules.size == 1 -> when (val module = selectedModules.first()) {
        is PackageSearchModule.Base -> SearchData.SingleBaseModule(
            searchParameters = SearchPackagesRequest(
                packagesType = module.compatiblePackageTypes,
                searchQuery = searchQuery
            ),
            module = module
        )

        is PackageSearchModule.WithVariants -> {
            SearchData.SingleWithVariantsModule(
                searchForVariant = module.variants
                    .values
                    .groupBy { it.compatiblePackageTypes }
                    .map { (compatiblePackages, supportedVariants) ->
                        SearchData.SingleWithVariantsModule.SearchForVariants(
                            searchParameters = SearchPackagesRequest(
                                packagesType = compatiblePackages,
                                searchQuery = searchQuery
                            ),
                            compatibleVariants = supportedVariants
                        )
                    },
                module = module
            )
        }
    }
    else -> SearchData.MultipleModules(
        searchParameters = SearchPackagesRequest(
            packagesType = buildPackageTypes {
                val types = selectedModules.map { it.compatiblePackageTypes }
                if (types.all { it.any { it is PackagesType.Maven } })
                    mavenPackages()
                if (types.all { it.any { it is PackagesType.Gradle } })
                    gradlePackages { }
                if (types.all { it.any { it is PackagesType.Npm } })
                    npmPackages()
                if (types.all { it.any { it is PackagesType.Cocoapods } })
                    cocoapodsPackages { }
            },
            searchQuery = searchQuery,
        ),
        modules = selectedModules
    )
}
