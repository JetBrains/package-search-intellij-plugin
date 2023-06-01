package org.jetbrains.packagesearch.plugin.core.nitrite

import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModuleBuilder
import org.dizitart.no2.Nitrite
import org.dizitart.no2.NitriteBuilder
import org.dizitart.no2.WriteResult
import org.dizitart.no2.objects.ObjectFilter
import org.dizitart.no2.objects.ObjectRepository
import org.dizitart.no2.objects.filters.ObjectFilters
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository

@Serializable
data class CacheEntry<T>(
    val data: T,
    @SerialName("_id") val id: String,
    val lastUpdate: Instant = Clock.System.now()
)

typealias ApiPackageCacheEntry = CacheEntry<ApiPackage>
typealias ApiRepositoryCacheEntry = CacheEntry<List<ApiRepository>>

inline fun <reified T : Any> ObjectRepository<T>.coroutine(coroutineScope: CoroutineScope) =
    CoroutineObjectRepository(this, coroutineScope)

suspend inline fun <reified T : Any> CoroutineObjectRepository<T>.insert(items: Collection<T>): WriteResult =
    insert(items.toTypedArray())

suspend inline fun <reified T : Any> CoroutineObjectRepository<T>.insert(item: T): WriteResult =
    insert(arrayOf(item))

fun ApiPackage.asEntry() = CacheEntry(this, id)
fun List<ApiRepository>.asEntry() = CacheEntry(this, "knownRepositories")

fun inFilter(field: String, ids: Collection<String>): ObjectFilter = ObjectFilters.`in`(field, *ids.toTypedArray())

fun Nitrite.asCoroutine(coroutineScope: CoroutineScope) =
    CoroutineNitrite(this, coroutineScope)

fun NitriteBuilder.kotlinxNitriteMapper(function: SerializersModuleBuilder.() -> Unit): NitriteBuilder =
    nitriteMapper(KotlinxNitriteMapper(function))