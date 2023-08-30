package com.jetbrains.packagesearch.plugin.utils

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.dizitart.no2.objects.ObjectFilter
import org.jetbrains.packagesearch.api.v3.ApiPackage
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchApiPackagesContext
import com.jetbrains.packagesearch.plugin.core.nitrite.*
import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient

class PackageSearchApiPackageCache(
    private val fileCache: CoroutineObjectRepository<ApiPackageCacheEntry>,
    private val apiClient: PackageSearchApiClient,
    private val maxAge: Duration = 1.days / 2,
) : PackageSearchApiPackagesContext {

    private val cachesMutex = Mutex()

    private val weakIdCache: Cache<String, ApiPackage> =
        Caffeine.newBuilder().weakValues().build()
    private val weakHashCache: Cache<String, ApiPackage> =
        Caffeine.newBuilder().weakValues().build()

    override suspend fun getPackageInfoByIds(packageIds: Set<String>) =
        getPackages(
            ids = packageIds,
            weakMap = weakIdCache,
            apiCall = { apiClient.getPackageInfoByIds(it) }
        ) { NitriteFilters.Object.`in`(ApiPackageCacheEntry::data / ApiPackage::id, it) }

    override suspend fun getPackageInfoByIdHashes(packageIdHashes: Set<String>): Map<String, ApiPackage> =
        getPackages(
            ids = packageIdHashes,
            weakMap = weakHashCache,
            apiCall = { apiClient.getPackageInfoByIdHashes(it) }
        ) { NitriteFilters.Object.`in`(ApiPackageCacheEntry::data / ApiPackage::idHash, it) }

    private suspend fun getPackages(
        ids: Set<String>,
        weakMap: Cache<String, ApiPackage>,
        apiCall: suspend (Set<String>) -> List<ApiPackage>,
        query: (Set<String>) -> ObjectFilter,
    ): Map<String, ApiPackage> = cachesMutex.withLock {
        val inMemoryResults: Map<String, ApiPackage> = weakMap.getAllPresent(ids)
        var missingIds = ids - inMemoryResults.keys
        return if (missingIds.isNotEmpty()) {
            val localDatabaseResults = fileCache.find(query(missingIds))
                .filter { Clock.System.now() < it.lastUpdate + maxAge }
                .map { it.data }
                .toList()
                .associateBy { it.id }
            val localCacheResults = inMemoryResults + localDatabaseResults
            weakMap.putAll(localDatabaseResults)
            missingIds = ids - localCacheResults.keys
            if (missingIds.isNotEmpty()) {
                val networkResults = apiCall(missingIds)
                    .associateBy { it.id }
                val packageEntries = networkResults.values.map { it.asCacheEntry() }
                // TODO cache also miss in network to avoid pointless empty query
                if (networkResults.isNotEmpty()) {
                    fileCache.remove(NitriteFilters.Object.`in`(
                        path = ApiPackageCacheEntry::data / ApiPackage::id,
                        value = packageEntries.map { it.data.id }
                    ))
                    fileCache.insert(packageEntries)

                    weakMap.putAll(networkResults)
                    localCacheResults + networkResults
                } else localCacheResults
            } else localCacheResults
        } else inMemoryResults
    }
}