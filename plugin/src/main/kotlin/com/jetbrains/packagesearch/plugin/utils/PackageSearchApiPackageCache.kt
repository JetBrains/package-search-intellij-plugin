package com.jetbrains.packagesearch.plugin.utils

import com.jetbrains.packagesearch.plugin.core.nitrite.NitriteFilters
import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import com.jetbrains.packagesearch.plugin.core.nitrite.insert
import com.jetbrains.packagesearch.plugin.core.utils.suspendSafe
import korlibs.crypto.SHA256
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
            useHashes = false
        )

    override suspend fun getPackageInfoByIdHashes(ids: Set<String>): Map<String, ApiPackage> =
        getPackages(
            ids = ids,
            useHashes = true
        )

    override suspend fun searchPackages(request: SearchPackagesRequest): List<ApiPackage> {
        val sha = SHA256.digest(Json.encodeToString(request).toByteArray()).base64
        val contextName = "${Random.nextInt()} | ${this::class}#searchPackages"
        logDebug(contextName) { "Searching for packages | searchSha = $sha" }
        val cachedEntry = searchCache.find(NitriteFilters.Object.eq(ApiSearchEntry::searchHash, sha))
            .singleOrNull()
        if (cachedEntry != null) {
            val isOffline = !isOnline()
            val isCacheValid = cachedEntry.lastUpdate + maxAge > Clock.System.now()
            if (isOffline || isCacheValid) {
                logDebug(contextName) {
                    "Using cached search results because `isOffline = $isOffline || isCacheValid = $isCacheValid` | searchSha = $sha"
                }
                return cachedEntry.packages
            }
            searchCache.remove(NitriteFilters.Object.eq(ApiSearchEntry::searchHash, sha))
        }
        logDebug(contextName) { "Fetching search results from the server | searchSha = $sha" }
        return apiClient.searchPackages(request)
            .also { searchCache.insert(ApiSearchEntry(it, sha, request)) }
    }

    override suspend fun getKnownRepositories(): List<ApiRepository> {
        val cached = repositoryCache.find().singleOrNull()
        val isOnlineStatus = isOnline()
        if (cached != null && (Clock.System.now() < cached.lastUpdate + maxAge || !isOnlineStatus)) {
            return cached.data
        }
        return when {
            isOnlineStatus -> runCatching { apiClient.getKnownRepositories() }
                .suspendSafe()
                .onSuccess {
                    repositoryCache.removeAll()
                    repositoryCache.insert(ApiRepositoryCacheEntry(it))
                }
                .getOrDefault(cached?.data ?: emptyList())

            else -> emptyList()
        }
    }

    private suspend fun getPackages(
        ids: Set<String>,
        useHashes: Boolean,
    ): Map<String, ApiPackage> = cachesMutex.withLock {
        if (ids.isEmpty()) return emptyMap()
        val contextName = "${Random.nextInt()} | ${this::class.qualifiedName}#getPackages"
        logDebug(contextName) { "Downloading packages | ids.size = ${ids.size}" }
        val isOnlineStatus = isOnline()
        val packageIdSelector = when {
            useHashes -> ApiPackageCacheEntry::packageIdHash
            else -> ApiPackageCacheEntry::packageId
        }
        val apiCall = when {
            useHashes -> apiClient::getPackageInfoByIdHashes
            else -> apiClient::getPackageInfoByIds
        }

        // retrieve the packages from the local database
        val localDatabaseResults = apiPackageCache
            .find(NitriteFilters.Object.`in`(packageIdSelector, ids))
            .filter { if (isOnlineStatus) Clock.System.now() < it.lastUpdate + maxAge else true }
            .toList()

        // evaluate packages that are missing from the local database
        val missingIds = ids - when {
            useHashes -> localDatabaseResults.mapNotNull { it.packageIdHash }.toSet()
            else -> localDatabaseResults.mapNotNull { it.packageId }.toSet()
        }
        logDebug(contextName) { "Missing packages | missingIds.size = ${missingIds.size}" }

        // filter away packages that are unknown in our backend
        val localDatabaseResultsData = localDatabaseResults
            .mapNotNull { it.data }
            .associateBy { it.id }
        when {
            missingIds.isEmpty() || !isOnlineStatus -> {
                logDebug(contextName) {
                    "Using cached packages only | isOnline = $isOnlineStatus, localDatabaseResultsData.size = ${localDatabaseResultsData.size}"
                }
                localDatabaseResultsData
            }

            else -> {
                // retrieve the packages from the network
                val networkResults = runCatching { apiCall(missingIds) }
                    .suspendSafe()
                    .onFailure { logDebug("${this::class.qualifiedName}#getPackages", it) }
                if (networkResults.isSuccess) {
                    val packageEntries = networkResults.getOrThrow()
                        .values
                        .map { it.asCacheEntry() }
                    if (packageEntries.isNotEmpty()) {
                        logDebug(contextName) { "No packages found | missingIds.size = ${missingIds.size}" }

                        // remove the old entries
                        apiPackageCache.remove(
                            filter = NitriteFilters.Object.`in`(
                                path = packageIdSelector,
                                value = packageEntries.mapNotNull { it.packageId }
                            )
                        )
                        logDebug(contextName) {
                            "Removing old entries | packageEntries.size = ${packageEntries.size}"
                        }
                    }
                    // evaluate packages that are missing from our backend
                    val retrievedPackageIds =
                        packageEntries.mapNotNull { if (useHashes) it.packageIdHash else it.packageId }
                            .toSet()
                    val unknownPackages = missingIds.minus(retrievedPackageIds)
                        .map { id ->
                            when {
                                useHashes -> ApiPackageCacheEntry(packageIdHash = id)
                                else -> ApiPackageCacheEntry(packageId = id)
                            }
                        }
                    logDebug(contextName) {
                        "New unknown packages | unknownPackages.size = ${unknownPackages.size}"
                    }
                    // insert the new entries
                    val toInsert = packageEntries + unknownPackages
                    if (toInsert.isNotEmpty()) apiPackageCache.insert(toInsert)
                }
                val networkResultsData = networkResults.getOrDefault(emptyMap())
                logDebug(contextName) {
                    "Using network results and caches | networkResults.size = ${networkResultsData.size}, " +
                            "localDatabaseResultsData = ${localDatabaseResultsData.size}"
                }
                localDatabaseResultsData + networkResultsData
            }
        }
    }
}

