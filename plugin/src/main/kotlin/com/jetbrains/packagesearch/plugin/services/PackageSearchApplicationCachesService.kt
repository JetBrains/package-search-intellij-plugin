@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.services

import com.intellij.ide.actions.cache.AsyncRecoveryResult
import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.ide.actions.cache.RecoveryScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.util.io.delete
import com.jetbrains.packagesearch.mock.SonatypeApiClient
import com.jetbrains.packagesearch.mock.client.PackageSearchSonatypeApiClient
import com.jetbrains.packagesearch.plugin.PackageSearch
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.nitrite.buildDefaultNitrate
import com.jetbrains.packagesearch.plugin.core.nitrite.div
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI
import com.jetbrains.packagesearch.plugin.core.utils.registryFlow
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModelNodeProcessor
import com.jetbrains.packagesearch.plugin.http.NitriteKtorCache
import com.jetbrains.packagesearch.plugin.http.SerializableCachedResponseData
import com.jetbrains.packagesearch.plugin.utils.ApiPackageCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiRepositoryCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiSearchEntry
import com.jetbrains.packagesearch.plugin.utils.KtorDebugLogger
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiPackageCache
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApplicationCachesService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.utils.logDebug
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import java.util.concurrent.CompletableFuture
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.IndexType
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchEndpoints

@Service(Level.APP)
class PackageSearchApplicationCachesService(private val coroutineScope: CoroutineScope) : Disposable, RecoveryAction {

    companion object {
        private val cacheFilePath = cacheDir / "cache-${PackageSearch.pluginId}.db"

        private val cacheDir
            get() = appSystemDir / "packagesearch"
    }

    @PKGSInternalAPI
    val cache = buildDefaultNitrate(
        path = appSystemDir
            .resolve(cacheFilePath)
            .apply { parent.toFile().mkdirs() }
            .absolutePathString()
    )

    override fun dispose() {
        cache.close()
    }

    private inline fun <reified T : Any> getRepository(key: String) =
        cache.getRepository<T>(key)

    private val sonatypeCacheRepository
        get() = getRepository<SerializableCachedResponseData>("sonatype-cache")

    private val packagesRepository
        get() = getRepository<ApiPackageCacheEntry>("packages")

    private val searchesRepository
        get() = getRepository<ApiSearchEntry>("searches")

    private val repositoryCache
        get() = getRepository<ApiRepositoryCacheEntry>("repositories")

    private val sonatypeApiClient = PackageSearchSonatypeApiClient(
        httpClient = SonatypeApiClient.defaultHttpClient {
            install(Logging) {
                level = LogLevel.HEADERS
                logger = KtorDebugLogger()
            }
            install(HttpCache) {
                publicStorage(NitriteKtorCache(sonatypeCacheRepository))
            }
        },
        onError = { logDebug("Error while retrieving packages from Sonatype", it) }
    )
    private val devApiClient = PackageSearchApiClient(
        endpoints = PackageSearchEndpoints.DEV,
        httpClient = PackageSearchApiClient.defaultHttpClient {
            install(Logging) {
                level = LogLevel.ALL
                logger = KtorDebugLogger()
            }
        }
    )

    private val apiClientTypeStateFlow = IntelliJApplication
        .registryFlow("packagesearch.sonatype.api.client")
        .map { if (it) sonatypeApiClient else devApiClient }
        .stateIn(coroutineScope, SharingStarted.Eagerly, sonatypeApiClient)

    val apiPackageCache = apiClientTypeStateFlow
        .map { PackageSearchApiPackageCache(packagesRepository, searchesRepository, it) }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = PackageSearchApiPackageCache(
                apiPackageCache = packagesRepository,
                searchCache = searchesRepository,
                apiClient = sonatypeApiClient
            )
        )

    init {
        apiClientTypeStateFlow
            .onEach { IntelliJApplication.PackageSearchApplicationCachesService.clearCaches() }
            .launchIn(coroutineScope)
        coroutineScope.launch { createIndexes() }
        coroutineScope.launch(Dispatchers.IO) {
            val toDelete = cacheDir.walk()
                .sortedByDescending { it.getLastModifiedTime() }
                .filterNot { it == cacheFilePath }
                .drop(2)
                .toList()
            toDelete.forEach { it.delete() }
            if (toDelete.isNotEmpty()) {
                logDebug {
                    buildString {
                        appendLine("Deleted ${toDelete.size} old caches:")
                        toDelete.forEach { appendLine("- ${it.relativeTo(appSystemDir)}") }
                    }
                }
            }
        }
        if (PackageSearch.deleteCachesOnStartup) {
            logDebug("Deleting caches on startup")
            coroutineScope.launch { clearCaches() }
        }
    }

    private suspend fun createIndexes() {
        searchesRepository.createIndex(
            indexOptions = IndexOptions.indexOptions(IndexType.Unique),
            path = ApiSearchEntry::searchHash
        )
        packagesRepository.createIndex(
            indexOptions = IndexOptions.indexOptions(IndexType.Unique),
            path = ApiPackageCacheEntry::data / ApiPackage::id
        )
        packagesRepository.createIndex(
            indexOptions = IndexOptions.indexOptions(IndexType.Unique),
            path = ApiPackageCacheEntry::data / ApiPackage::idHash
        )
        sonatypeCacheRepository.createIndex(
            indexOptions = IndexOptions.indexOptions(IndexType.NonUnique),
            path = SerializableCachedResponseData::url
        )
    }

    override val actionKey: String
        get() = "CleanPackageSearchApplicationCacheAction"

    override val performanceRate: Int
        get() = 0

    override val presentableName: String
        get() = PackageSearchBundle.message("packagesearch.cache.clean")

    override fun perform(recoveryScope: RecoveryScope): CompletableFuture<AsyncRecoveryResult> =
        coroutineScope.future(Dispatchers.IO) {
            runCatching { recoveryScope.project.service<PackageSearchGradleModelNodeProcessor.Cache>().clean() }
            clearCaches()
            recoveryScope.project.PackageSearchProjectService.restart()
            AsyncRecoveryResult(recoveryScope, emptyList())
        }

    private suspend fun clearCaches() {
        searchesRepository.removeAll()
        packagesRepository.removeAll()
        repositoryCache.removeAll()
        sonatypeCacheRepository.removeAll()
    }
}

