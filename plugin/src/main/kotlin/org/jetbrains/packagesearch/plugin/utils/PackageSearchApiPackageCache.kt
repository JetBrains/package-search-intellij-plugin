package org.jetbrains.packagesearch.plugin.utils

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.dizitart.no2.objects.ObjectFilter
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.client.PackageSearchApiClient
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchApiPackagesProvider
import org.jetbrains.packagesearch.plugin.core.nitrite.*
import org.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import org.jetbrains.packagesearch.plugin.gradle.GradleModelCacheEntry
import org.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class PackageSearchApiPackageCache(
    private val fileCache: CoroutineObjectRepository<ApiPackageCacheEntry>,
    private val apiClient: PackageSearchApiClient,
    private val maxAge: Duration = 1.days / 2,
) : PackageSearchApiPackagesProvider {

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
        val localDatabaseResults = missingIds
            .takeIf { it.isNotEmpty() }
            ?.let { fileCache.find(query(it)) }
            ?.filter { it.lastUpdate + maxAge < Clock.System.now() }
            ?.map { it.data }
            ?.associateBy { it.id }
            ?: emptyMap()
        val localCacheResults = inMemoryResults + localDatabaseResults
        weakMap.putAll(localDatabaseResults)
        missingIds = ids - localCacheResults.keys
        return if (missingIds.isNotEmpty()) {
            val networkResults = apiCall(missingIds)
                .associateBy { it.id }
            networkResults.values.map { it.asCacheEntry() }
                .forEach { entry ->
                    fileCache.update(
                        filter = NitriteFilters.Object.eq(
                            path = ApiPackageCacheEntry::data / ApiPackage::id,
                            value = entry.data.id
                        ),
                        update = entry,
                        upsert = true
                    )
                }
            fileCache.insert(networkResults.values.map { it.asCacheEntry() })
            weakMap.putAll(networkResults)
            localCacheResults + networkResults
        } else localCacheResults
    }
}