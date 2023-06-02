@file:OptIn(InternalAPI::class)

package org.jetbrains.packagesearch.plugin.core.nitrite

import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.components.Service
import com.intellij.util.application
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.modules.contextual
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.IndexType
import org.dizitart.no2.Nitrite
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersionWeakCache
import org.jetbrains.packagesearch.plugin.core.nitrite.*
import org.jetbrains.packagesearch.plugin.core.utils.PackageSearchApiClientService
import kotlin.io.path.absolutePathString

@RequiresOptIn("This API is internal and you should not use it.")
annotation class InternalAPI

@Service(Service.Level.APP)
class PackageSearchCaches(coroutineScope: CoroutineScope) {

    @InternalAPI
    val repositories =
        mutableMapOf<String, CoroutineObjectRepository<*>>()

    @InternalAPI
    val mutex = Mutex()

    @Internal
    val localCache = coroutineScope.async(Dispatchers.IO) {
        Nitrite.builder()
            .kotlinxNitriteMapper {
                contextual(NormalizedVersionWeakCache)
            }
            .filePath(
                appSystemDir
                    .resolve("packagesearch/cache.db")
                    .apply { parent.toFile().mkdirs() }
                    .absolutePathString()
                    .also { println(it) }
            )
            .compressed()
            .openOrCreate()
            .asCoroutine(coroutineScope)
    }

    suspend inline fun <reified T : Any> getRepository(key: String): CoroutineObjectRepository<T> = mutex.withLock {
        if (key in repositories) {
            val repo = repositories[key]
            if (repo?.type == T::class) {
                @Suppress("UNCHECKED_CAST")
                return repo as CoroutineObjectRepository<T>
            } else {
                error("Repository $key is already registered with type ${repo?.type}")
            }
        } else {
            val repo = localCache.await().getRepository<T>(key)
            repositories[key] = repo
            return repo
        }
    }

    private val apiPackageCache = coroutineScope.async {
        getRepository<ApiPackageCacheEntry>("packages")
            .also {
                it.createIndex(
                    IndexOptions.indexOptions(IndexType.Unique),
                    ApiPackageCacheEntry::data,
                    ApiPackage::id
                )
                it.createIndex(
                    IndexOptions.indexOptions(IndexType.Unique),
                    ApiPackageCacheEntry::data,
                    ApiPackage::idHash
                )
            }
            .let { PackageSearchApiPackageCache(it, application.PackageSearchApiClientService.client) }
    }

    suspend fun getApiPackageCache() = apiPackageCache.await()

    suspend fun getRepositoryCache() =
        getRepository<ApiRepositoryCacheEntry>("repositories")

}