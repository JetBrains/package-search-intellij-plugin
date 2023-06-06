package org.jetbrains.packagesearch.plugin.core.nitrite

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.dizitart.no2.Nitrite
import org.dizitart.no2.NitriteBuilder
import org.dizitart.no2.NitriteContext
import org.dizitart.no2.WriteResult
import org.dizitart.no2.objects.ObjectRepository
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersionWeakCache
import org.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineNitrite
import org.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import org.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormat
import org.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentSerializerBuilder

private val NitriteContext.kotlinxMapperOrNull
    get() = nitriteMapper as? KotlinxNitriteMapper

@Serializable
data class ApiPackageCacheEntry(
    val data: ApiPackage,
    @SerialName("_id") val id: Long? = null,
    val lastUpdate: Instant = Clock.System.now(),
)

@Serializable
data class ApiRepositoryCacheEntry(
    val data: List<ApiRepository>,
    @SerialName("_id") val id: Long? = null,
    val lastUpdate: Instant = Clock.System.now(),
)

inline fun <reified T : Any> ObjectRepository<T>.coroutine(documentFormat: NitriteDocumentFormat) =
    CoroutineObjectRepository(this, documentFormat)

suspend inline fun <reified T : Any> CoroutineObjectRepository<T>.insert(items: Collection<T>): WriteResult =
    insert(items.toTypedArray())

suspend inline fun <reified T : Any> CoroutineObjectRepository<T>.insert(item: T): WriteResult =
    insert(arrayOf(item))

fun ApiPackage.asCacheEntry() = ApiPackageCacheEntry(this)
fun List<ApiRepository>.asCacheEntry() = ApiRepositoryCacheEntry(this)

fun Nitrite.asCoroutine(documentFormat: NitriteDocumentFormat? = null) =
    CoroutineNitrite(
        synchronous = this,
        documentFormat = documentFormat ?: context.kotlinxMapperOrNull?.nitriteDocumentFormat ?: error("Must use kotlinx mapper")
    )

fun NitriteBuilder.kotlinxNitriteMapper(function: NitriteDocumentSerializerBuilder.() -> Unit = {}): NitriteBuilder =
    nitriteMapper(KotlinxNitriteMapper(function))

fun CoroutineScope.buildDefaultNitrate(
    path: String,
    nitriteMapperConf: NitriteDocumentSerializerBuilder.() -> Unit = {
        ignoreUnknownsKeys = true
        serializersModule = SerializersModule {
            contextual(NormalizedVersionWeakCache)
        }
    },
) = async(Dispatchers.IO) {
    Nitrite.builder()
        .kotlinxNitriteMapper(nitriteMapperConf)
        .filePath(path)
        .compressed()
        .openOrCreate()
        .asCoroutine()
}