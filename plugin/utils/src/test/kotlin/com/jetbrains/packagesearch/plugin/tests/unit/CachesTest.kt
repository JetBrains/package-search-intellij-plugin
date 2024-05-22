package com.jetbrains.packagesearch.plugin.tests.unit

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.size
import com.jetbrains.packagesearch.plugin.utils.ApiPackageCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiRepositoryCacheEntry
import com.jetbrains.packagesearch.plugin.utils.ApiSearchCacheEntry
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiPackageCache
import com.jetbrains.packagesearch.plugin.utils.asCacheEntry
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.dizitart.kno2.getRepository
import org.dizitart.kno2.insert
import org.dizitart.kno2.loadModule
import org.dizitart.kno2.nitrite
import org.dizitart.kno2.serialization.KotlinXSerializationMapper
import org.dizitart.no2.mvstore.MVStoreModule
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchEndpoints
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class CachesTest {

    val localCachesDbPath
        get() = Path(System.getenv("CACHES"), "local-caches.db")

    private val db = nitrite {
        validateRepositories = false
        loadModule(KotlinXSerializationMapper)
        loadModule(
            MVStoreModule.withConfig()
                .filePath(
                    localCachesDbPath
                        .createParentDirectories()
                        .absolutePathString()
                )
                .build()
        )
    }

    val apiPackageCaches
        get() = db.getRepository<ApiPackageCacheEntry>("apiPackageCache")

    val searchCaches
        get() = db.getRepository<ApiSearchCacheEntry>("searchCache")

    val repositoryCaches
        get() = db.getRepository<ApiRepositoryCacheEntry>("apiRepositoryCache")

    fun getCache(
        isOnline: Boolean = true,
        maxAge: Duration = 1.hours,
        engine: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { handleHttpRequest(it) },
    ) =
        PackageSearchApiPackageCache(
            apiPackageCache = apiPackageCaches,
            searchCache = searchCaches,
            repositoryCache = repositoryCaches,
            apiClient = PackageSearchApiClient(
                endpoints = PackageSearchEndpoints.DEFAULT,
                httpClient = PackageSearchApiClient.defaultHttpClient(MockEngine(engine))
            ),
            maxAge = maxAge,
            logger = TestLogger,
            isOnline = { isOnline }
        )

    suspend fun getTestPackageIds() =
        getTestPackages()
            .map { it.id }
            .toSet()

    private suspend fun getTestPackages() =
        Json.decodeFromStream<List<ApiPackage>>(getResource("/mock/responses/package_info.json"))

    @BeforeEach
    fun setup() = runTest {
        apiPackageCaches.clear()
        searchCaches.clear()
        repositoryCaches.clear()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `assert packages being cached`() = runTest {
        val cache = getCache()
        val packages = cache.getPackageInfoByIds(getTestPackageIds())
        assertThat(packages)
            .size()
            .isEqualTo(3)

        val cachedPackages = apiPackageCaches.find().toList()
        assertThat(cachedPackages)
            .size()
            .isEqualTo(3)

    }

    @Test
    fun `assert that http requests are not repeated`() = runTest {
        var requestCount = 0
        val cache = getCache {
            requestCount++
            handleHttpRequest(it)
        }

        cache.getPackageInfoByIds(getTestPackageIds())
        cache.getPackageInfoByIds(getTestPackageIds())

        assertThat(requestCount).isEqualTo(1)
    }

    @Test
    fun `assert that http requests are repeated when cache is invalidated`() = runTest {
        var requestCount = 0
        val cache = getCache(
            maxAge = Duration.ZERO,
            engine = {
                requestCount++
                handleHttpRequest(it)
            }
        )

        cache.getPackageInfoByIds(getTestPackageIds())
        cache.getPackageInfoByIds(getTestPackageIds())

        assertThat(requestCount).isEqualTo(2)
    }

    @Test
    fun `assert that no http calls are made when offline`() = runTest {
        var requestCount = 0
        val cache = getCache(isOnline = false) {
            requestCount++
            handleHttpRequest(it)
        }

        val packages = cache.getPackageInfoByIds(getTestPackageIds())

        assertThat(requestCount).isEqualTo(0)
        assertThat(packages)
            .size()
            .isEqualTo(0)
    }

    @Test
    fun `assert that local caches are used when offline`() = runTest {
        val cache = getCache(isOnline = false)
        apiPackageCaches.insert(getTestPackages().map { it.asCacheEntry() })
        val packages = cache.getPackageInfoByIds(getTestPackageIds())

        assertThat(packages)
            .size()
            .isEqualTo(3)
    }
}

