package org.jetbrains.packagesearch.plugin.nitrite

import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.serializer
import org.dizitart.no2.Document
import org.dizitart.no2.mapper.NitriteMapper

fun KotlinxNitriteMapper(builderAction: SerializersModuleBuilder.() -> Unit) =
    KotlinxNitriteMapper(NitriteDocument(builderAction))

class KotlinxNitriteMapper(private val nitriteDocument: NitriteDocument = NitriteDocument()) : NitriteMapper {
    override fun <T : Any> asDocument(`object`: T): Document {
        return nitriteDocument.encodeToDocument(
            serializer = nitriteDocument.serializersModule.serializer(`object`.javaClass),
            element = `object`
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> asObject(document: Document, type: Class<T>): T = nitriteDocument.decodeFromDocument(
        serializer = nitriteDocument.serializersModule.serializer(type),
        document = document
    ) as T

    override fun isValueType(`object`: Any?) = false

    override fun asValue(`object`: Any?) = `object`

}