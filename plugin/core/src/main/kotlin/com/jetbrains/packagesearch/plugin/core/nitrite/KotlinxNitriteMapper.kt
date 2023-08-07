package com.jetbrains.packagesearch.plugin.core.nitrite

import kotlinx.serialization.serializer
import org.dizitart.no2.Document
import org.dizitart.no2.mapper.NitriteMapper
import com.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormat
import com.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormatBuilder

fun KotlinxNitriteMapper(
    from: NitriteDocumentFormat = NitriteDocumentFormat.Default,
    builderAction: NitriteDocumentFormatBuilder.() -> Unit
) = KotlinxNitriteMapper(NitriteDocumentFormat(from, builderAction))

class KotlinxNitriteMapper(
    val nitriteDocumentFormat: NitriteDocumentFormat = NitriteDocumentFormat.Default,
) : NitriteMapper {

    override fun <T : Any> asDocument(`object`: T): Document =
        nitriteDocumentFormat.encodeToDocument(
            serializer = nitriteDocumentFormat.serializersModule.serializer(`object`.javaClass),
            element = `object`
        )

    @Suppress("UNCHECKED_CAST")
    override fun <T> asObject(document: Document, type: Class<T>): T =
        nitriteDocumentFormat.decodeFromDocument(
            serializer = nitriteDocumentFormat.serializersModule.serializer(type),
            document = document
        ) as T

    override fun isValueType(`object`: Any?) = true
    override fun asValue(`object`: Any?) = `object`

}
