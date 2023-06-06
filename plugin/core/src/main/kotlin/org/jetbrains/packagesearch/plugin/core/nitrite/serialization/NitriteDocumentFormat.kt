package org.jetbrains.packagesearch.plugin.core.nitrite.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.dizitart.no2.Document

class NitriteDocumentFormat(
    val serializersModule: SerializersModule = EmptySerializersModule(),
    val ignoreUnknownsKeys: Boolean = false
) {

    fun <T> encodeToDocument(serializer: SerializationStrategy<T>, element: T): Document {
        val encoder = DocumentEncoder(serializersModule)
        encoder.encodeSerializableValue(serializer, element)
        return encoder.result
    }

    fun <T: Any> decodeFromDocument(serializer: DeserializationStrategy<T>, document: Document): T {
        val decoder = DocumentDecoder(Deserializable.Doc(document), serializersModule, ignoreUnknownsKeys)
        return decoder.decodeSerializableValue(serializer)
    }
}