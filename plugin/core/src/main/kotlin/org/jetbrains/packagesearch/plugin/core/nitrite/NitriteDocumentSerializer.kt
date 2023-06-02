package org.jetbrains.packagesearch.plugin.core.nitrite

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.serializer
import org.dizitart.no2.Document

fun NitriteDocumentSerializer(builderAction: SerializersModuleBuilder.() -> Unit) =
    NitriteDocumentSerializer(SerializersModule(builderAction))

class NitriteDocumentSerializer(
    val serializersModule: SerializersModule = EmptySerializersModule()
) {

    fun <T> encodeToDocument(serializer: SerializationStrategy<T>, element: T): Document {
        val encoder = DocumentEncoder(serializersModule)
        encoder.encodeSerializableValue(serializer, element)
        return encoder.result
    }

    fun <T: Any> decodeFromDocument(serializer: DeserializationStrategy<T>, document: Document): T {
        val decoder = DocumentDecoder(Deserializable.Doc(document), serializersModule)
        return decoder.decodeSerializableValue(serializer)
    }
}

inline fun <reified T> NitriteDocumentSerializer.encodeToDocument(element: T) =
    encodeToDocument(serializer<T>(), element)