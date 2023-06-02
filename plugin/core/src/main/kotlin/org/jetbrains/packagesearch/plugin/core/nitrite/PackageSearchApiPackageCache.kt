package org.jetbrains.packagesearch.plugin.core.nitrite

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.client.PackageSearchApiClient
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchApiPackagesProvider
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class PackageSearchApiPackageCache(
    private val fileCache: CoroutineObjectRepository<ApiPackageCacheEntry>,
    private val apiClient: PackageSearchApiClient,
    val maxAge: Duration = 1.days / 2,
) : PackageSearchApiPackagesProvider {

    private val cachesMutex = Mutex()

    private val weakIdCache = WeakHashMap<String, ApiPackage>()
    private val weakHashCache = WeakHashMap<String, ApiPackage>()

    override suspend fun getPackageInfoByIds(packageIds: Set<String>): Map<String, ApiPackage> = cachesMutex.withLock {
        val cached = weakIdCache.filterKeys { it in packageIds }
        var missingIds = packageIds - cached.keys
        val databaseResults = fileCache.find(inFilter("_id", missingIds))
            .filter { it.lastUpdate + maxAge < Clock.System.now() }
            .map { it.data }
            .toList()
            .associateBy { it.id }
        val cachesResult = cached + databaseResults
        weakIdCache.putAll(databaseResults)
        missingIds = packageIds - cachesResult.keys
        if (missingIds.isNotEmpty()) {
            val networkResults = apiClient.getPackageInfoByIds(missingIds)
                .associateBy { it.id }
            fileCache.insert(networkResults.values.map { it.asNewCacheEntry() })
            weakIdCache.putAll(networkResults)
            cachesResult + networkResults
        } else cachesResult
    }

    override suspend fun getPackageInfoByIdHashes(packageIdHashes: Set<String>): Map<String, ApiPackage> = cachesMutex.withLock {
        val cached = weakHashCache.filterKeys { it in packageIdHashes }
        var missingHashes = packageIdHashes - cached.keys
        val databaseResults = fileCache.find(inFilter("data.idHash", missingHashes))
            .filter { it.lastUpdate + maxAge < Clock.System.now() }
            .map { it.data }
            .toList()
            .associateBy { it.id }
        val cachesResult = cached + databaseResults
        weakHashCache.putAll(databaseResults)
        missingHashes = packageIdHashes - cachesResult.keys
        if (missingHashes.isNotEmpty()) {
            val networkResults = apiClient.getPackageInfoByIdHashes(missingHashes)
                .associateBy { it.id }
            fileCache.insert(networkResults.values.map { it.asNewCacheEntry() })
            weakHashCache.putAll(networkResults)
            cachesResult + networkResults
        } else cachesResult
    }
}