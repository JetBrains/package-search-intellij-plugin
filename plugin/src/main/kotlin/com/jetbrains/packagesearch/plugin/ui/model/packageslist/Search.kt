package com.jetbrains.packagesearch.plugin.ui.model.packageslist

import com.jetbrains.packagesearch.plugin.core.utils.suspendSafe
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

        val query: SearchPackagesRequest
        val apis: PackageSearchApi

        suspend fun execute(): Response

        data class Base(
            override val query: SearchPackagesRequest,
            override val apis: PackageSearchApi,
        ) : Query {
            override suspend fun execute(): Response.Base {
                val searchResult =
                    kotlin.runCatching { apis.searchPackages(query) }
                        .suspendSafe()
                        .map { Response.Base.Success(it) }
                val error = searchResult.exceptionOrNull()
                return if (error != null) Response.Base.Error(error) else searchResult.getOrThrow()
            }

        }

        data class WithVariants(
            override val query: SearchPackagesRequest,
            override val apis: PackageSearchApi,
            override val attributes: List<String>,
            override val primaryVariantName: String,
            override val additionalVariants: List<String>,
        ) : Query, Search.WithVariants {
            override suspend fun execute(): Response.WithVariants {
                val searchResult =
                    kotlin.runCatching { apis.searchPackages(query) }
                        .suspendSafe()
                        .map { Response.WithVariants.Success(it, attributes, primaryVariantName, additionalVariants) }
                val error = searchResult.exceptionOrNull()
                return when {
                    error != null ->
                        Response.WithVariants.Error(error, attributes, primaryVariantName, additionalVariants)

                    else -> searchResult.getOrThrow()
                }
            }
        }

    }

    sealed interface Response : Search {

        sealed interface Base : Response {

            data class Success(val packages: List<ApiPackage>) : Base
            data class Error(val error: Throwable) : Base
        }

        sealed interface WithVariants : Response, Search.WithVariants {

            data class Success(
                val packages: List<ApiPackage>,
                override val attributes: List<String>,
                override val primaryVariantName: String,
                override val additionalVariants: List<String>,
            ) : WithVariants

            data class Error(
                val error: Throwable,
                override val attributes: List<String>,
                override val primaryVariantName: String,
                override val additionalVariants: List<String>,
            ) : WithVariants
        }

    }
}