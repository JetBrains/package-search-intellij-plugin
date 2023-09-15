package com.jetbrains.packagesearch.plugin.utils

import com.jetbrains.packagesearch.plugin.core.nitrite.NitriteFilters
import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import com.jetbrains.packagesearch.plugin.core.nitrite.div
import com.jetbrains.packagesearch.plugin.core.nitrite.insert
import korlibs.crypto.SHA256
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dizitart.no2.objects.ObjectFilter
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApi
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest

class PackageSearchApiPackageCache(
    private val apiPackageCache: CoroutineObjectRepository<ApiPackageCacheEntry>,
    private val searchCache: CoroutineObjectRepository<ApiSearchEntry>,
    private val apiClient: PackageSearchApi,
    private val maxAge: Duration = Random.nextDouble(0.5, 1.0).days,
) : PackageSearchApi by apiClient {

    private val cachesMutex = Mutex()

    override suspend fun getPackageInfoByIds(ids: Set<String>) =
        getPackages(
            ids = ids,
            apiCall = { apiClient.getPackageInfoByIds(it) },
            query = { NitriteFilters.Object.`in`(ApiPackageCacheEntry::data / ApiPackage::id, it) }
        )

    override suspend fun getPackageInfoByIdHashes(ids: Set<String>): Map<String, ApiPackage> =
        getPackages(
            ids = ids,
            apiCall = { apiClient.getPackageInfoByIdHashes(it) },
            query = { NitriteFilters.Object.`in`(ApiPackageCacheEntry::data / ApiPackage::idHash, it) }
        )

    override suspend fun searchPackages(request: SearchPackagesRequest): List<ApiPackage> {
        val sha = SHA256.digest(Json.encodeToString(request).toByteArray()).base64
        val cachedEntry = searchCache.find(NitriteFilters.Object.eq(ApiSearchEntry::searchHash, sha))
            .singleOrNull()

        if (cachedEntry != null && cachedEntry.lastUpdate + maxAge > Clock.System.now()) {
            if (cachedEntry.lastUpdate + maxAge > Clock.System.now()) {
                return getPackageInfoByIds(cachedEntry.packagesIds.toSet()).values.toList()
            }
            searchCache.remove(cachedEntry)
        }

        val searchResult = apiClient.searchPackageIds(request)
        searchCache.insert(ApiSearchEntry(searchResult, sha, request))
        val packages = searchResult.takeIf { it.isNotEmpty() }
            ?.let { getPackageInfoByIds(it.toSet()).values.toList() }
            ?: emptyList()
        if (packages.isNotEmpty()) {
            apiPackageCache.remove(
                NitriteFilters.Object.`in`(
                    path = ApiPackageCacheEntry::data / ApiPackage::id,
                    value = searchResult
                )
            )
            apiPackageCache.insert(packages.map { it.asCacheEntry() })
            return packages
        }
        return emptyList()
    }

    private suspend fun getPackages(
        ids: Set<String>,
        apiCall: suspend (Set<String>) -> Map<String, ApiPackage>,
        query: (Set<String>) -> ObjectFilter,
    ): Map<String, ApiPackage> = cachesMutex.withLock {
        val localDatabaseResults = apiPackageCache.find(query(ids))
            .filter { Clock.System.now() < it.lastUpdate + maxAge }
            .map { it.data }
            .toList()
            .associateBy { it.id }
        val missingIds = ids - localDatabaseResults.keys
        if (missingIds.isNotEmpty()) {
            val networkResults = apiCall(missingIds)
            // TODO cache also miss in network to avoid pointless empty query
            if (networkResults.isNotEmpty()) {
                val packageEntries = networkResults.values.map { it.asCacheEntry() }
                apiPackageCache.remove(NitriteFilters.Object.`in`(
                    path = ApiPackageCacheEntry::data / ApiPackage::id,
                    value = packageEntries.map { it.data.id }
                ))
                apiPackageCache.insert(packageEntries)
                localDatabaseResults + networkResults
            } else localDatabaseResults
        } else localDatabaseResults
    }
}