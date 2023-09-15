package com.jetbrains.packagesearch.plugin.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import org.jetbrains.packagesearch.api.v3.ApiPackage
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest

@Serializable
data class ApiPackageCacheEntry(
    val data: ApiPackage,
    @SerialName("_id") val id: Long? = null,
    val lastUpdate: Instant = Clock.System.now(),
)

@Serializable
data class ApiSearchEntry(
    val packagesIds: List<String>,
    val searchHash: String,
    val original: SearchPackagesRequest,
    @SerialName("_id") val id: Long? = null,
    val lastUpdate: Instant = Clock.System.now(),
)

@Serializable
data class ApiRepositoryCacheEntry(
    val data: List<ApiRepository>,
    @SerialName("_id") val id: Long? = null,
    val lastUpdate: Instant = Clock.System.now(),
)

fun ApiPackage.asCacheEntry() = ApiPackageCacheEntry(this)
fun List<ApiRepository>.asCacheEntry() = ApiRepositoryCacheEntry(this)