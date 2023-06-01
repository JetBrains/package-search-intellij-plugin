package org.jetbrains.packagesearch.plugin.core.nitrite

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import org.dizitart.no2.Document

fun NitriteDocument(builderAction: SerializersModuleBuilder.() -> Unit) =
    NitriteDocument(SerializersModule(builderAction))

class NitriteDocument(val serializersModule: SerializersModule = EmptySerializersModule()) {
    fun <T> encodeToDocument(serializer: SerializationStrategy<T>, element: T): Document {
        val encoder = DocumentEncoder(serializersModule)
        encoder.encodeSerializableValue(serializer, element)
        return encoder.result
    }

    fun <T: Any> decodeFromDocument(serializer: DeserializationStrategy<T>, document: Document): T {
        val decoder = DocumentDecoder(document, serializersModule)
        return decoder.decodeSerializableValue(serializer)
    }
}