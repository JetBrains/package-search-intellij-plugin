package org.jetbrains.packagesearch.plugin

import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import org.jetbrains.packagesearch.plugin.nitrite.*
import org.jetbrains.packagesearch.plugin.remote.PackageSearchApiClient
import org.jetbrains.packagesearch.plugin.utils.ApiPackageCache
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersionWeakCache
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
            .filePath(appSystemDir.resolve("packagesearch/cache.db").absolutePathString())
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

    suspend fun getApiPackageCache() = getRepository<ApiPackageCacheEntry>("packages")
        .also { it.createIndex("data.idHash", IndexOptions.indexOptions(IndexType.Unique)) }
        .let { ApiPackageCache(it, application.service<PackageSearchApiClient>()) }

    suspend fun getRepositoryCache() =
            getRepository<ApiRepositoryCacheEntry>("repositories")

}