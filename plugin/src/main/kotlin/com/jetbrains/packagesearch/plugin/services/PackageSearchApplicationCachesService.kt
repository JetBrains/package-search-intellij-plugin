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
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.PackageSearch
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.nitrite.buildDefaultNitrate
import com.jetbrains.packagesearch.plugin.core.nitrite.div
import com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModelNodeProcessor
import com.jetbrains.packagesearch.plugin.http.SerializableCachedResponseData
import com.jetbrains.packagesearch.plugin.utils.ApiPackageCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiRepositoryCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiSearchEntry
import com.jetbrains.packagesearch.plugin.utils.KtorDebugLogger
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiPackageCache
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import java.util.concurrent.CompletableFuture
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.IndexType
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchEndpoints

@Service(Level.APP)
class PackageSearchApplicationCachesService(private val coroutineScope: CoroutineScope) : Disposable, RecoveryAction {

    // for 232 compatibility
    constructor() : this(CoroutineScope(SupervisorJob()))

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
        if ("232" in PackageSearch.intelliJVersion) {
            coroutineScope.cancel()
        }
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

    private val devApiClient = PackageSearchApiClient(
        endpoints = PackageSearchEndpoints.DEV,
        httpClient = PackageSearchApiClient.defaultHttpClient {
            install(Logging) {
                level = LogLevel.ALL
                logger = KtorDebugLogger()
            }
        }
    )

    val apiPackageCache = PackageSearchApiPackageCache(packagesRepository, searchesRepository, devApiClient)

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

    init {
        coroutineScope.launch { createIndexes() }
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

