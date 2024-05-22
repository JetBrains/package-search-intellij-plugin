package com.jetbrains.packagesearch.plugin.utils

import korlibs.crypto.SHA256
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dizitart.kno2.filters.eq
import org.dizitart.no2.collection.UpdateOptions
import org.dizitart.no2.filters.FluentFilter
import org.dizitart.no2.repository.ObjectRepository
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.RefreshPackagesInfoRequest
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest


class PackageSearchApiPackageCache(
    private val apiPackageCache: ObjectRepository<ApiPackageCacheEntry>,
    private val searchCache: ObjectRepository<ApiSearchCacheEntry>,
    private val repositoryCache: ObjectRepository<ApiRepositoryCacheEntry>,
    private val apiClient: PackageSearchApiClient,
    private val maxAge: Duration = Random.nextDuration(24.hours, 26.hours),
    private val logger: PluginLogger,
    private val isOnline: () -> Boolean,
) {

    private suspend fun getPackages(
        ids: Set<String>,
        useHash: Boolean,
    ): Map<String, ApiPackage> {
        val contextName = "${this::class.simpleName}#getPackages | ${Random.nextInt(0, 1000)}, useHash: $useHash"
        val field = when {
            useHash -> ApiPackageCacheEntry::packageIdHash
            else -> ApiPackageCacheEntry::packageId
        }
        logger.logDebug(contextName) { "Fetching packages | ids = $ids" }
        val query = FluentFilter.where(field.name).`in`(ids)
        val knownPackageCacheEntries = withContext(Dispatchers.IO) { apiPackageCache.find(query).toList() }
        logger.logDebug(contextName) { "Retrieved ${knownPackageCacheEntries.size} known packages from the cache | ids = $ids" }
        val knownPackagesMap = knownPackageCacheEntries
            .mapNotNull { it.data }
            .associateBy { it.id }

        if (!isOnline()) {
            logger.logDebug(contextName) { "Returning known packages from the cache because the device is offline | ids = $ids" }
            return knownPackagesMap
        }

        val staleIds = knownPackageCacheEntries
            .filter { it.lastUpdated + maxAge < Clock.System.now() }
            .mapNotNull { it.packageIdHash }
            .toSet()

        logger.logDebug(contextName) { "Stale ids.size: ${staleIds.size}" }

        val toFetch = ids.hashIfNeeded(useHash) - knownPackageCacheEntries.getIds(true) + staleIds
        if (toFetch.isEmpty()) {
            return knownPackagesMap
        }
        logger.logDebug(contextName) { "To fetch ids.size: ${toFetch.size}" }
        val cacheRequests = toFetch.map {
            RefreshPackagesInfoRequest.CacheRequest(
                packageIdHash = it,
                latestKnownVersion = knownPackagesMap[it]?.versions?.latest?.normalized?.versionName
            )
        }
        val refreshedPackages =
            kotlin.runCatching { apiClient.refreshPackagesInfo(RefreshPackagesInfoRequest(cacheRequests)) }
                .suspendSafe()
                .onFailure { logger.logDebug(contextName, it) { "Error while refreshing packages." } }
                .getOrDefault(emptyList())
                .associateBy { it.id }

        if (refreshedPackages.isEmpty()) {
            logger.logDebug(contextName) { "No packages were fetched from the server | ids = $ids" }
            return knownPackagesMap
        }

        refreshedPackages.values.forEach {
            logger.logDebug(contextName) { "Refreshing cache with id:${it.id}" }
            apiPackageCache.update(
                /* filter = */ ApiPackageCacheEntry::packageId eq it.id,
                /* update = */ it.asCacheEntry(),
                UpdateOptions.updateOptions(true)
            )
        }

        logger.logDebug(contextName) { "Fetched ${refreshedPackages.size} packages from the server | ids = $ids" }
        val unknownIds = ids - knownPackagesMap.getIds(useHash) - refreshedPackages.getIds(useHash)
        logger.logDebug(contextName) { "Unknown ids.size: ${unknownIds.size}" }
        unknownIds.forEach { id ->
            when {
                useHash -> apiPackageCache.update(
                    /* filter = */ ApiPackageCacheEntry::packageIdHash eq id,
                    /* update = */ ApiPackageCacheEntry(packageIdHash = id),
                    /* updateOptions = */ UpdateOptions.updateOptions(true)
                )

                else -> apiPackageCache.update(
                    /* filter = */ ApiPackageCacheEntry::packageId eq id,
                    /* update = */ ApiPackageCacheEntry(packageId = id),
                    /* updateOptions = */ UpdateOptions.updateOptions(true)
                )
            }
        }
        return knownPackagesMap + refreshedPackages
    }

    suspend fun getPackageInfoByIds(ids: Set<String>): Map<String, ApiPackage> =
        getPackages(ids = ids, useHash = false)

    suspend fun getPackageInfoByIdHashes(ids: Set<String>): Map<String, ApiPackage> =
        getPackages(ids = ids, useHash = true)

    suspend fun searchPackages(
        request: SearchPackagesRequest,
    ): List<ApiPackage> {
        val sha = SHA256.digest(Json.encodeToString(request).toByteArray()).base64
        val contextName = "${Random.nextInt()} | ${this::class}#searchPackages"
        logger.logDebug(contextName) { "Searching for packages | searchSha = $sha" }
        val cachedEntry = withContext(Dispatchers.IO) {
            searchCache.find(ApiSearchCacheEntry::searchHash eq sha)
                .singleOrNull()
        }
        if (cachedEntry != null) {
            val isOffline = !isOnline()
            val isCacheValid = cachedEntry.lastUpdate + maxAge > Clock.System.now()
            if (isOffline || isCacheValid) {
                logger.logDebug(contextName) {
                    "Using cached search results because `isOffline = $isOffline || isCacheValid = $isCacheValid` | searchSha = $sha"
                }
                return cachedEntry.packages
            }
            searchCache.remove(ApiSearchCacheEntry::searchHash eq sha)
        }
        logger.logDebug(contextName) { "Fetching search results from the server | searchSha = $sha" }
        return apiClient.searchPackages(request)
            .also { searchCache.insert(ApiSearchCacheEntry(it, sha, request)) }
    }

    suspend fun getKnownRepositories(): List<ApiRepository> {
        val cached = withContext(Dispatchers.IO) { repositoryCache.find().singleOrNull() }
        val isOnlineStatus = isOnline()
        if (cached != null && (Clock.System.now() < cached.lastUpdate + maxAge || !isOnlineStatus)) {
            return cached.data
        }
        return when {
            isOnlineStatus -> runCatching { apiClient.getKnownRepositories() }
                .suspendSafe()
                .onSuccess {
                    repositoryCache.clear()
                    repositoryCache.insert(ApiRepositoryCacheEntry(it))
                }
                .getOrDefault(cached?.data ?: emptyList())

            else -> emptyList()
        }
    }

}

private inline fun <reified T : Comparable<T>> FluentFilter.`in`(ids: Iterable<T>) =
    `in`(*ids.toList().toTypedArray())
