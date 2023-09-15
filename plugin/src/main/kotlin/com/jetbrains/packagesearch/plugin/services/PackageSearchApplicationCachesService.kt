package com.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.util.application
import com.jetbrains.packagesearch.plugin.core.nitrite.buildDefaultNitrate
import com.jetbrains.packagesearch.plugin.core.nitrite.div
import com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI
import com.jetbrains.packagesearch.plugin.utils.ApiPackageCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiRepositoryCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiSearchEntry
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiClientService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiPackageCache
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.IndexType
import org.jetbrains.packagesearch.api.v3.ApiPackage

@Service(Level.APP)
class PackageSearchApplicationCachesService(coroutineScope: CoroutineScope) {

    @PKGSInternalAPI
    val cache = coroutineScope.buildDefaultNitrate(
        path = appSystemDir
            .resolve("packagesearch/cache.db")
            .apply { parent.toFile().mkdirs() }
            .absolutePathString()
    )

    suspend inline fun <reified T : Any> getRepository(key: String) =
        cache.await().getRepository<T>(key)

    suspend fun getPackagesRepository() =
        getRepository<ApiPackageCacheEntry>("packages")
            .also {
                it.createIndex(
                    indexOptions = IndexOptions.indexOptions(IndexType.Unique),
                    path = ApiPackageCacheEntry::data / ApiPackage::id
                )
                it.createIndex(
                    indexOptions = IndexOptions.indexOptions(IndexType.Unique),
                    path = ApiPackageCacheEntry::data / ApiPackage::idHash
                )
            }

    suspend fun getSearchesRepository() =
        getRepository<ApiSearchEntry>("searches")
            .also {
                it.createIndex(
                    indexOptions = IndexOptions.indexOptions(IndexType.Unique),
                    path = ApiSearchEntry::searchHash
                )
            }

    suspend fun getRepositoryCache() =
        getRepository<ApiRepositoryCacheEntry>("repositories")

    val apiPackageCache = application.PackageSearchApiClientService.client
        .map { PackageSearchApiPackageCache(getPackagesRepository(), getSearchesRepository(), it.client) }


}

