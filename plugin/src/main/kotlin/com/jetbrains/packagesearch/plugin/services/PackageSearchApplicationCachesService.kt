package com.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.components.Service
import com.intellij.util.application
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.IndexType
import org.jetbrains.packagesearch.api.v3.ApiPackage
import com.jetbrains.packagesearch.plugin.core.nitrite.*
import com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiClientService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiPackageCache
import kotlin.io.path.absolutePathString

@Service(Service.Level.APP)
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

    private val apiPackageCache = coroutineScope.async {
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
            .let { PackageSearchApiPackageCache(it, application.PackageSearchApiClientService.client) }
    }

    suspend fun getApiPackageCache() = apiPackageCache.await()

    suspend fun getRepositoryCache() =
        getRepository<ApiRepositoryCacheEntry>("repositories")
}

