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
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.nitrite.buildDefaultNitrate
import com.jetbrains.packagesearch.plugin.core.nitrite.div
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI
import com.jetbrains.packagesearch.plugin.core.utils.registryFlow
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModelNodeProcessor
import com.jetbrains.packagesearch.plugin.utils.ApiPackageCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiRepositoryCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiSearchEntry
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiPackageCache
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApplicationCachesService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import java.util.concurrent.CompletableFuture
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.IndexType
import org.jetbrains.packagesearch.api.v3.ApiPackage

@Service(Level.APP)
class PackageSearchApplicationCachesService(private val coroutineScope: CoroutineScope) : Disposable, RecoveryAction {

    @PKGSInternalAPI
    val cache = buildDefaultNitrate(
        path = appSystemDir
            .resolve("packagesearch/cache.db")
            .apply { parent.toFile().mkdirs() }
            .absolutePathString()
    )

    override fun dispose() {
        cache.close()
    }

    inline fun <reified T : Any> getRepository(key: String) =
        cache.getRepository<T>(key)

    val sonatypeCacheRepository
        get() = getRepository<SerializableCachedResponseData>("sonatype-cache")

    val packagesRepository
        get() = getRepository<ApiPackageCacheEntry>("packages")

    val searchesRepository
        get() = getRepository<ApiSearchEntry>("searches")

    val repositoryCache
        get() = getRepository<ApiRepositoryCacheEntry>("repositories")

    val apiClientTypeStateFlow = IntelliJApplication
        .registryFlow("org.jetbrains.packagesearch.sonatype")
        .map { if (it) PackageSearchApiClientType.Sonatype(sonatypeCacheRepository) else PackageSearchApiClientType.Dev }
        .stateIn(coroutineScope, SharingStarted.Eagerly, PackageSearchApiClientType.Sonatype(sonatypeCacheRepository))

    val apiPackageCache = apiClientTypeStateFlow
        .map { PackageSearchApiPackageCache(packagesRepository, searchesRepository, it.client) }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = PackageSearchApiPackageCache(
                apiPackageCache = packagesRepository,
                searchCache = searchesRepository,
                apiClient = apiClientTypeStateFlow.value.client
            )
        )

    init {
        coroutineScope.launch { createIndexes() }
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

    override fun perform(recoveryScope: RecoveryScope): CompletableFuture<AsyncRecoveryResult> {
        return coroutineScope.future(Dispatchers.IO) {

            runCatching { recoveryScope.project.service<PackageSearchGradleModelNodeProcessor.Cache>().clean() }
            searchesRepository.removeAll()
            packagesRepository.removeAll()
            repositoryCache.removeAll()
            sonatypeCacheRepository.removeAll()
            recoveryScope.project.PackageSearchProjectService.restart()
            AsyncRecoveryResult(recoveryScope, emptyList())
        }
    }
}

class CleanPackageSearchApplicationCacheAction :
    RecoveryAction by IntelliJApplication.PackageSearchApplicationCachesService
