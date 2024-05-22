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
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI
import com.jetbrains.packagesearch.plugin.utils.ApiPackageCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiRepositoryCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiSearchCacheEntry
import com.jetbrains.packagesearch.plugin.utils.KtorDebugLogger
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiPackageCache
import com.jetbrains.packagesearch.plugin.utils.PackageSearchLogger
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.headers
import java.util.concurrent.CompletableFuture
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dizitart.kno2.getRepository
import org.dizitart.kno2.loadModule
import org.dizitart.kno2.nitrite
import org.dizitart.kno2.serialization.KotlinXSerializationMapper
import org.dizitart.no2.index.IndexOptions
import org.dizitart.no2.index.IndexType
import org.dizitart.no2.mvstore.MVStoreModule
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchEndpoints

@Service(Level.APP)
class PackageSearchApplicationCachesService(private val coroutineScope: CoroutineScope) : Disposable, RecoveryAction {

    companion object {
        private val cacheFilePath
            get() = appSystemDir / "caches" / "packagesearch" / "db-v${PackageSearch.databaseVersion}.db"
    }

    @PKGSInternalAPI
    val cache = nitrite {
        validateRepositories = false
        loadModule(KotlinXSerializationMapper)
        loadModule(
            MVStoreModule.withConfig()
                .filePath(
                    cacheFilePath
                        .createParentDirectories()
                        .absolutePathString()
                )
                .build()
        )
    }

    override fun dispose() {
        cache.close()
    }

    private val packagesRepository
        get() = cache.getRepository<ApiPackageCacheEntry>("packages")

    private val searchesRepository
        get() = cache.getRepository<ApiSearchCacheEntry>("searches")

    private val repositoryCache
        get() = cache.getRepository<ApiRepositoryCacheEntry>("repositories")

    private val apiClient = PackageSearchApiClient(
        endpoints = PackageSearchEndpoints.PROD,
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
        logger = PackageSearchLogger,
        isOnline = { isOnlineFlow.value }
    )

    private suspend fun createIndexes() = withContext(Dispatchers.IO) {
        searchesRepository.createIndex(
            IndexOptions.indexOptions(IndexType.UNIQUE),
            ApiSearchCacheEntry::searchHash.name
        )
        packagesRepository.createIndex(
            IndexOptions.indexOptions(IndexType.UNIQUE),
            ApiPackageCacheEntry::packageId.name
        )
        packagesRepository.createIndex(
            IndexOptions.indexOptions(IndexType.UNIQUE),
            ApiPackageCacheEntry::packageIdHash.name
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

    private suspend fun clearCaches() = withContext(Dispatchers.IO) {
        searchesRepository.clear()
        packagesRepository.clear()
        repositoryCache.clear()
    }
}


