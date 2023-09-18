package com.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.jetbrains.packagesearch.plugin.core.nitrite.buildDefaultNitrate
import com.jetbrains.packagesearch.plugin.core.nitrite.div
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI
import com.jetbrains.packagesearch.plugin.core.utils.registryFlow
import com.jetbrains.packagesearch.plugin.utils.ApiPackageCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiRepositoryCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiSearchEntry
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiPackageCache
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.IndexType
import org.jetbrains.packagesearch.api.v3.ApiPackage

@Service(Level.APP)
class PackageSearchApplicationCachesService(coroutineScope: CoroutineScope) : Disposable {

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

    fun getSonatypeCacheRepository() =
        getRepository<SerializableCachedResponseData>("sonatype-cache")

    fun getPackagesRepository() =
        getRepository<ApiPackageCacheEntry>("packages")

    fun getSearchesRepository() =
        getRepository<ApiSearchEntry>("searches")

    fun getRepositoryCache() =
        getRepository<ApiRepositoryCacheEntry>("repositories")

    val apiClientTypeStateFlow = IntelliJApplication
        .registryFlow("org.jetbrains.packagesearch.sonatype")
        .map { if (it) PackageSearchApiClientType.Sonatype(getSonatypeCacheRepository()) else PackageSearchApiClientType.Dev }
        .stateIn(coroutineScope, SharingStarted.Eagerly, PackageSearchApiClientType.Sonatype(getSonatypeCacheRepository()))

    val apiPackageCache = apiClientTypeStateFlow
        .map { PackageSearchApiPackageCache(getPackagesRepository(), getSearchesRepository(), it.client) }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = PackageSearchApiPackageCache(
                apiPackageCache = getPackagesRepository(),
                searchCache = getSearchesRepository(),
                apiClient = apiClientTypeStateFlow.value.client
            )
        )

    init {
        coroutineScope.launch {
            getPackagesRepository().also {
                it.createIndex(
                    indexOptions = IndexOptions.indexOptions(IndexType.Unique),
                    path = ApiPackageCacheEntry::data / ApiPackage::id
                )
                it.createIndex(
                    indexOptions = IndexOptions.indexOptions(IndexType.Unique),
                    path = ApiPackageCacheEntry::data / ApiPackage::idHash
                )
            }
            getPackagesRepository().also {
                it.createIndex(
                    indexOptions = IndexOptions.indexOptions(IndexType.Unique),
                    path = ApiPackageCacheEntry::data / ApiPackage::id
                )
                it.createIndex(
                    indexOptions = IndexOptions.indexOptions(IndexType.Unique),
                    path = ApiPackageCacheEntry::data / ApiPackage::idHash
                )
            }
            getSonatypeCacheRepository().also {
                it.createIndex(
                    indexOptions = IndexOptions.indexOptions(IndexType.NonUnique),
                    path = SerializableCachedResponseData::url
                )
            }
        }
    }
}

