package org.jetbrains.packagesearch.plugin.core.nitrite

import kotlinx.serialization.serializer
import org.dizitart.no2.Document
import org.dizitart.no2.mapper.NitriteMapper
import org.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormat
import org.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentSerializer
import org.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentSerializerBuilder
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf

fun KotlinxNitriteMapper(builderAction: NitriteDocumentSerializerBuilder.() -> Unit) =
    KotlinxNitriteMapper(NitriteDocumentSerializer(builderAction))

class KotlinxNitriteMapper(
    val nitriteDocumentFormat: NitriteDocumentFormat = NitriteDocumentFormat(),
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
