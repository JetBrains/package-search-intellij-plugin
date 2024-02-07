@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.services

import com.intellij.ide.actions.cache.AsyncRecoveryResult
import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.ide.actions.cache.RecoveryScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.PackageSearch
import com.jetbrains.packagesearch.plugin.core.nitrite.buildDefaultNitrate
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI
import com.jetbrains.packagesearch.plugin.utils.ApiPackageCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiRepositoryCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiSearchEntry
import com.jetbrains.packagesearch.plugin.utils.KtorDebugLogger
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiPackageCache
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.headers
import java.util.concurrent.CompletableFuture
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.IndexType
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchEndpoints

@Service(Level.APP)
class PackageSearchApplicationCachesService : RecoveryAction, Disposable {


    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob())

    companion object {
        private val cacheFilePath
            get() = cacheDirectory / "db-${PackageSearch.pluginVersion}.db"

        private val cacheDirectory
            get() = appSystemDir / "caches" / "packagesearch"
    }

    @PKGSInternalAPI
    val cache = buildDefaultNitrate(
        path = cacheFilePath
            .apply { parent.toFile().mkdirs() }
            .absolutePathString()
    )

    override fun dispose() {
        cache.close()
        coroutineScope.cancel()
    }

    private inline fun <reified T : Any> getRepository(key: String) =
        cache.getRepository<T>(key)

    private val packagesRepository
        get() = getRepository<ApiPackageCacheEntry>("packages")

    private val searchesRepository
        get() = getRepository<ApiSearchEntry>("searches")

    private val repositoryCache
        get() = getRepository<ApiRepositoryCacheEntry>("repositories")

    private val apiClient = PackageSearchApiClient(
        endpoints = PackageSearchEndpoints.DEFAULT,
        httpClient = PackageSearchApiClient.defaultHttpClient(Java) {
            install(Logging) {
                level = LogLevel.ALL
                logger = KtorDebugLogger()
                filter { it.attributes.getOrNull(PackageSearchApiClient.Attributes.Cache) == true }
            }
            install(DefaultRequest) {
                headers {
                    append("JB-Plugin-Version", PackageSearch.pluginVersion)
                    append("JB-IDE-Version", IntelliJApplication.service<ApplicationInfo>().strictVersion)
                }
            }
        }
    )

    val isOnlineFlow = apiClient.isOnlineFlow()
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), true)

    val apiPackageCache = PackageSearchApiPackageCache(
        apiPackageCache = packagesRepository,
        repositoryCache = repositoryCache,
        searchCache = searchesRepository,
        apiClient = apiClient,
        isOnline = { isOnlineFlow.value }
    )

    private suspend fun createIndexes() {
        searchesRepository.createIndex(
            indexOptions = IndexOptions.indexOptions(IndexType.Unique),
            path = ApiSearchEntry::searchHash
        )
        packagesRepository.createIndex(
            indexOptions = IndexOptions.indexOptions(IndexType.Unique),
            path = ApiPackageCacheEntry::packageId
        )
        packagesRepository.createIndex(
            indexOptions = IndexOptions.indexOptions(IndexType.Unique),
            path = ApiPackageCacheEntry::packageIdHash
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
            clearCaches()
            recoveryScope.project.PackageSearchProjectService.restart()
            AsyncRecoveryResult(recoveryScope, emptyList())
        }

    private suspend fun clearCaches() {
        searchesRepository.removeAll()
        packagesRepository.removeAll()
        repositoryCache.removeAll()
    }
}

