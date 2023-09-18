package com.jetbrains.packagesearch.plugin.core.nitrite

import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineNitrite
import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import com.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormat
import com.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormatBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.dizitart.no2.Nitrite
import org.dizitart.no2.NitriteBuilder
import org.dizitart.no2.NitriteContext
import org.dizitart.no2.WriteResult
import org.dizitart.no2.objects.ObjectRepository
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersionWeakCache

private val NitriteContext.kotlinxMapperOrNull
    get() = nitriteMapper as? KotlinxNitriteMapper

inline fun <reified T : Any> ObjectRepository<T>.coroutine(documentFormat: NitriteDocumentFormat) =
    CoroutineObjectRepository(this, documentFormat)

suspend inline fun <reified T : Any> CoroutineObjectRepository<T>.insert(items: Collection<T>): WriteResult =
    insert(items.toTypedArray())

suspend inline fun <reified T : Any> CoroutineObjectRepository<T>.insert(item: T): WriteResult =
    insert(arrayOf(item))

fun Nitrite.asCoroutine(documentFormat: NitriteDocumentFormat? = null) =
    CoroutineNitrite(
        synchronous = this,
        documentFormat = documentFormat ?: context.kotlinxMapperOrNull?.nitriteDocumentFormat
        ?: error("Must use kotlinx mapper")
    )

fun NitriteBuilder.kotlinxNitriteMapper(
    from: NitriteDocumentFormat = NitriteDocumentFormat.Default,
    builderAction: NitriteDocumentFormatBuilder.() -> Unit = {},
): NitriteBuilder = nitriteMapper(KotlinxNitriteMapper(from, builderAction))

fun buildDefaultNitrate(
    path: String,
    nitriteMapperConf: NitriteDocumentFormatBuilder.() -> Unit = {
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            contextual(NormalizedVersionWeakCache)
        }
    },
) = Nitrite.builder()
    .kotlinxNitriteMapper(builderAction = nitriteMapperConf)
    .filePath(path)
    .compressed()
    .openOrCreate()
    .asCoroutine()
