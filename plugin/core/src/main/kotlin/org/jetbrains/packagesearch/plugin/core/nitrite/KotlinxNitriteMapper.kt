package org.jetbrains.packagesearch.plugin.core.nitrite

import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.serializer
import org.dizitart.no2.Document
import org.dizitart.no2.mapper.NitriteMapper

fun KotlinxNitriteMapper(builderAction: SerializersModuleBuilder.() -> Unit) =
    KotlinxNitriteMapper(NitriteDocumentSerializer(builderAction))

class KotlinxNitriteMapper(
    private val nitriteDocumentSerializer: NitriteDocumentSerializer = NitriteDocumentSerializer()
) : NitriteMapper {

    override fun <T : Any> asDocument(`object`: T): Document =
        nitriteDocumentSerializer.encodeToDocument(
            serializer = nitriteDocumentSerializer.serializersModule.serializer(`object`.javaClass),
            element = `object`
        )

    @Suppress("UNCHECKED_CAST")
    override fun <T> asObject(document: Document, type: Class<T>): T =
        nitriteDocumentSerializer.decodeFromDocument(
            serializer = nitriteDocumentSerializer.serializersModule.serializer(type),
            document = document
        ) as T

    fun <T> asObject(document: Document, type: java.lang.reflect.Type): T =
        nitriteDocumentSerializer.decodeFromDocument(
            serializer = nitriteDocumentSerializer.serializersModule.serializer(type),
            document = document
        ) as T

    override fun isValueType(`object`: Any?) = true
    override fun asValue(`object`: Any?) = `object`

}