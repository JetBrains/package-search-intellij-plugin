package com.jetbrains.packagesearch.plugin.ui.model.packageslist

import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApi
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest

sealed interface Search {

    interface WithVariants {
        val attributes: List<String>
        val primaryVariantName: String
        val additionalVariants: List<String>
    }

    sealed interface Query : Search {

        suspend fun execute(): Results

        data class Base(
            val query: SearchPackagesRequest,
            val apis: PackageSearchApi,
        ) : Query {
            override suspend fun execute(): Results.Base =
                Results.Base(apis.searchPackages(query))
        }

        data class WithVariants(
            val query: SearchPackagesRequest,
            val apis: PackageSearchApi,
            override val attributes: List<String>,
            override val primaryVariantName: String,
            override val additionalVariants: List<String>,
        ) : Query, Search.WithVariants {
            override suspend fun execute(): Results =
                Results.WithVariants(
                    packages = apis.searchPackages(query),
                    attributes = attributes,
                    primaryVariantName = primaryVariantName,
                    additionalVariants = additionalVariants
                )
        }

    }

    sealed interface Results : Search {
        val packages: List<ApiPackage>

        data class Base(override val packages: List<ApiPackage>) : Results

        data class WithVariants(
            override val packages: List<ApiPackage>,
            override val attributes: List<String>,
            override val primaryVariantName: String,
            override val additionalVariants: List<String>,
        ) : Results, Search.WithVariants
    }
}