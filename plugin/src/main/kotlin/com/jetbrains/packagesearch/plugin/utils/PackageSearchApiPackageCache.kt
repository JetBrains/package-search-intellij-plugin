package com.jetbrains.packagesearch.plugin.utils

import com.jetbrains.packagesearch.plugin.core.nitrite.NitriteFilters
import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import com.jetbrains.packagesearch.plugin.core.nitrite.insert
import korlibs.crypto.SHA256
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dizitart.no2.objects.ObjectFilter
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApi
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest

class PackageSearchApiPackageCache(
    private val apiPackageCache: CoroutineObjectRepository<ApiPackageCacheEntry>,
    private val searchCache: CoroutineObjectRepository<ApiSearchEntry>,
    private val repositoryCache: CoroutineObjectRepository<ApiRepositoryCacheEntry>,
    private val apiClient: PackageSearchApi,
    private val maxAge: Duration = Random.nextDouble(0.5, 1.0).days,
    private val isOnline: () -> Boolean,
) : PackageSearchApi by apiClient {

    private val cachesMutex = Mutex()

    override suspend fun getPackageInfoByIds(ids: Set<String>) =
        getPackages(
            ids = ids,
            apiCall = { apiClient.getPackageInfoByIds(it) },
            query = { NitriteFilters.Object.`in`(ApiPackageCacheEntry::packageId, it) },
            useHashes = false
        )

    override suspend fun getPackageInfoByIdHashes(ids: Set<String>): Map<String, ApiPackage> =
        getPackages(
            ids = ids,
            apiCall = { apiClient.getPackageInfoByIdHashes(it) },
            query = { NitriteFilters.Object.`in`(ApiPackageCacheEntry::packageIdHash, it) },
            useHashes = true
        )

    override suspend fun searchPackages(request: SearchPackagesRequest): List<ApiPackage> {
        val sha = SHA256.digest(Json.encodeToString(request).toByteArray()).base64
        val cachedEntry = searchCache.find(NitriteFilters.Object.eq(ApiSearchEntry::searchHash, sha))
            .singleOrNull()

        if (cachedEntry != null) {
            if (cachedEntry.lastUpdate + maxAge > Clock.System.now()) {
                return cachedEntry.packages
            }
            searchCache.remove(NitriteFilters.Object.eq(ApiSearchEntry::searchHash, sha))
        }

        return apiClient.searchPackages(request)
            .also { searchCache.insert(ApiSearchEntry(it, sha, request)) }
    }

    override suspend fun getKnownRepositories(): List<ApiRepository> {
        val cached = repositoryCache.find().singleOrNull()
        if (cached != null && (Clock.System.now() < cached.lastUpdate + maxAge || !isOnline())) {
            return cached.data
        }
        return if (isOnline()) apiClient.getKnownRepositories()
            .also {
                repositoryCache.removeAll()
                repositoryCache.insert(ApiRepositoryCacheEntry(it))
            }
        else emptyList()
    }

    private suspend fun getPackages(
        ids: Set<String>,
        apiCall: suspend (Set<String>) -> Map<String, ApiPackage>,
        query: (Set<String>) -> ObjectFilter,
        useHashes: Boolean,
    ): Map<String, ApiPackage> = cachesMutex.withLock {
        if (ids.isEmpty()) return emptyMap()
        val localDatabaseResults = apiPackageCache.find(query(ids))
            .filter { if (isOnline()) Clock.System.now() < it.lastUpdate + maxAge else true }
            .toList()
        val missingIds = ids - when {
            useHashes -> localDatabaseResults.map { it.packageIdHash }.toSet()
            else -> localDatabaseResults.map { it.packageId }.toSet()
        }

        val localDatabaseResultsData = localDatabaseResults
            .mapNotNull { it.data }
            .associateBy { it.id }
        when {
            missingIds.isEmpty() || !isOnline() -> localDatabaseResultsData
            else -> {
                val networkResults = runCatching { apiCall(missingIds) }
                    .onFailure { if (it is CancellationException) throw it }
                    .onFailure { logDebug("${this::class.qualifiedName}#getPackages", it) }
                    .getOrNull()
                    ?: emptyMap()
                if (networkResults.isNotEmpty()) {
                    val packageEntries = networkResults.values.map { it.asCacheEntry() }
                    apiPackageCache.remove(
                        filter = NitriteFilters.Object.`in`(
                            path = ApiPackageCacheEntry::packageId,
                            value = packageEntries.map { it.packageId }
                        )
                    )
                    apiPackageCache.insert(packageEntries)
                }
                localDatabaseResultsData + networkResults
            }
        }
    }
}

