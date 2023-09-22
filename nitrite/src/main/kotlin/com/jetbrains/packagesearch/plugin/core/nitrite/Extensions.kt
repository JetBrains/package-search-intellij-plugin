package com.jetbrains.packagesearch.plugin.core.nitrite

import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineNitrite
import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import com.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormat
import com.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormatBuilder
import java.io.File
import org.dizitart.no2.Nitrite
import org.dizitart.no2.NitriteBuilder
import org.dizitart.no2.NitriteContext
import org.dizitart.no2.WriteResult
import org.dizitart.no2.objects.ObjectRepository

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
    nitriteMapperConf: NitriteDocumentFormatBuilder.() -> Unit = {},
) = Nitrite.builder()
    .kotlinxNitriteMapper(builderAction = nitriteMapperConf)
    .filePath(path)
    .compressed()
    .openOrCreate()
    .asCoroutine()

fun buildDefaultNitrate(
    file: File,
    nitriteMapperConf: NitriteDocumentFormatBuilder.() -> Unit = {},
) = Nitrite.builder()
    .kotlinxNitriteMapper(builderAction = nitriteMapperConf)
    .filePath(file)
    .compressed()
    .openOrCreate()
    .asCoroutine()