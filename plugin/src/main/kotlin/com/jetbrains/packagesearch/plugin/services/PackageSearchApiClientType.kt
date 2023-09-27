package com.jetbrains.packagesearch.plugin.services

import com.jetbrains.packagesearch.mock.SonatypeApiClient
import com.jetbrains.packagesearch.mock.client.PackageSearchSonatypeApiClient
import com.jetbrains.packagesearch.plugin.core.nitrite.NitriteFilters
import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import com.jetbrains.packagesearch.plugin.utils.KtorDebugLogger
import com.jetbrains.packagesearch.plugin.utils.logDebug
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import io.ktor.util.toMap
import java.time.Month
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toSet
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApi
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchEndpoints

@Serializable
class SerializableCachedResponseData(
    val url: String,
    val statusCode: Int,
    val requestTime: LocalDateTime,
    val responseTime: LocalDateTime,
    val version: SerializableHttpProtocolVersion,
    val expires: LocalDateTime,
    val headers: Map<String, List<String>>,
    val varyKeys: Map<String, String>,
    val body: String,
)

@Serializable
data class SerializableHttpProtocolVersion(val name: String, val major: Int, val minor: Int)

fun HttpProtocolVersion.toSerializable() = SerializableHttpProtocolVersion(
    name = name,
    major = major,
    minor = minor
)

fun SerializableHttpProtocolVersion.toKtor() = HttpProtocolVersion(name, major, minor)

fun CachedResponseData.toSerializable() = SerializableCachedResponseData(
    url = url.toString(),
    statusCode = statusCode.value,
    requestTime = requestTime.toKotlinxLocalDateTime(),
    responseTime = responseTime.toKotlinxLocalDateTime(),
    version = version.toSerializable(),
    expires = expires.toKotlinxLocalDateTime(),
    headers = headers.toMap(),
    varyKeys = varyKeys,
    body = Base64.encode(body)
)

fun GMTDate.toKotlinxLocalDateTime() = LocalDateTime(
    year = year,
    month = Month.of(month.ordinal + 1),
    dayOfMonth = dayOfMonth,
    hour = hours,
    minute = minutes,
    second = seconds
)

fun LocalDateTime.toGMTDate() = GMTDate(
    year = year,
    month = io.ktor.util.date.Month.Companion.from(month.ordinal),
    dayOfMonth = dayOfMonth,
    hours = hour,
    minutes = minute,
    seconds = second
)

fun SerializableCachedResponseData.toKtor() = CachedResponseData(
    url = Url(url),
    statusCode = HttpStatusCode.fromValue(statusCode),
    requestTime = requestTime.toGMTDate(),
    responseTime = responseTime.toGMTDate(),
    version = version.toKtor(),
    expires = expires.toGMTDate(),
    headers = Headers.build {
        headers.forEach { (key, values) ->
            appendAll(key, values)
        }
    },
    varyKeys = varyKeys,
    body = Base64.decode(body)
)

class NitriteKtorCache(
    private val repository: CoroutineObjectRepository<SerializableCachedResponseData>,
) : CacheStorage {

    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? {
        val filter = NitriteFilters.Object.eq(
            path = SerializableCachedResponseData::url,
            value = url.toString()
        )
        return repository.find(filter)
            .singleOrNull()
            ?.toKtor()
    }

    override suspend fun findAll(url: Url): Set<CachedResponseData> {
        val filter = NitriteFilters.Object.eq(
            path = SerializableCachedResponseData::url,
            value = url.toString()
        )
        return repository.find(filter)
            .map { it.toKtor() }
            .toSet()
    }

    override suspend fun store(url: Url, data: CachedResponseData) {
        val filter = NitriteFilters.Object.eq(
            path = SerializableCachedResponseData::url,
            value = url.toString()
        )
        repository.update(
            filter = filter,
            update = data.toSerializable(),
            upsert = true
        )
    }


}

sealed interface PackageSearchApiClientType {

    val client: PackageSearchApi

    class Sonatype(repository: CoroutineObjectRepository<SerializableCachedResponseData>) : PackageSearchApiClientType {

        override val client: PackageSearchApi = PackageSearchSonatypeApiClient(
            httpClient = SonatypeApiClient.defaultHttpClient {
                install(Logging) {
                    level = LogLevel.INFO
                    logger = KtorDebugLogger()
                }
                install(HttpCache) {
                    publicStorage(NitriteKtorCache(repository))
                }
            },
            onError = { logDebug("Error while retrieving packages from Sonatype", it) }
        )
    }

    data object Dev : PackageSearchApiClientType {
        override val client: PackageSearchApi = PackageSearchApiClient(
            endpoints = PackageSearchEndpoints.DEV,
            httpClient = PackageSearchApiClient.defaultHttpClient {
                install(Logging) {
                    level = LogLevel.INFO
                    logger = KtorDebugLogger()
                }
            }
        )
    }
}
