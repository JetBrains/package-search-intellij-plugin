package com.jetbrains.packagesearch.plugin.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest

@Serializable
data class ApiPackageCacheEntry(
    val data: ApiPackage? = null,
    val packageId: String? = null,
    val packageIdHash: String? = null,
    @SerialName("_id") val id: String? = null,
    val lastUpdated: Instant = Clock.System.now(),
) {
    override fun toString() = buildString {
        append("ApiPackageCacheEntry(")
        if (packageId != null) {
            append("packageId=$packageId, ")
        } else if (packageIdHash != null) {
            append("packageIdHash=${packageIdHash.take(3)}...${packageIdHash.takeLast(3)}, ")
        }
        append("lastUpdated=$lastUpdated")
        append(")")
    }
}

@Serializable
data class ApiSearchCacheEntry(
    val packages: List<ApiPackage>,
    val searchHash: String,
    val original: SearchPackagesRequest,
    @SerialName("_id") val id: String? = null,
    val lastUpdate: Instant = Clock.System.now(),
) {
    override fun toString() = buildString {
        append("ApiSearchCacheEntry(")
        append("query=${original.searchQuery}, ")
        append("lastUpdate=$lastUpdate")
        append(")")
    }
}

@Serializable
data class ApiRepositoryCacheEntry(
    val data: List<ApiRepository>,
    @SerialName("_id") val id: String? = null,
    val lastUpdate: Instant = Clock.System.now(),
) {
    override fun toString() = buildString {
        append("ApiRepositoryCacheEntry(")
        append("lastUpdate=$lastUpdate")
        append(")")
    }

}

fun ApiPackage.asCacheEntry() = ApiPackageCacheEntry(this, id, idHash)