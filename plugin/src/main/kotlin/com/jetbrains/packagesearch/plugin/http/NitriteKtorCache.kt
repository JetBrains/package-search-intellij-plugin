package com.jetbrains.packagesearch.plugin.http

import com.jetbrains.packagesearch.plugin.core.nitrite.NitriteFilters
import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.CachedResponseData
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
    month = io.ktor.util.date.Month.from(month.ordinal),
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
        repository.update(
            filter = NitriteFilters.Object.eq(
                path = SerializableCachedResponseData::url,
                value = url.toString()
            ),
            update = data.toSerializable(),
            upsert = true
        )
    }

}