package org.jetbrains.packagesearch.plugin.nitrite

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule
import org.dizitart.no2.Document
import kotlin.properties.Delegates

class DocumentDecoder(
    private val document: Document,
    override val serializersModule: SerializersModule
) : AbstractDecoder() {
    // Keep track of nested documents during deserialization
    private val documentStack = mutableListOf<Document>()

    // A variable to hold the current key (property name) being deserialized
    private var currentKey: String by Delegates.notNull()

    // Iterator to go through the keys of the current document
    private val keysIterator = document.keys.iterator()

    // This method is called to determine the next element index to decode
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        // Check if there are no more keys left in the iterator
        if (!keysIterator.hasNext()) return CompositeDecoder.DECODE_DONE

        // Set the current key to the next key in the iterator
        currentKey = keysIterator.next()

        // Return the index of the current key in the descriptor
        return descriptor.getElementIndex(currentKey)
    }

    // This method is called to decode a primitive value
    override fun decodeValue(): Any {
        // Get the value corresponding to the current key from the current document
        return document[currentKey]
    }

    // This method is called when a nested structure (e.g., document, list, or another data class) is encountered
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        // Get the nested document corresponding to the current key
        val nestedDocument = document[currentKey] as? Document
            ?: throw SerializationException("Expected a nested Document at key $currentKey")

        // Push the current document onto the documentStack and set the nested document as the new current document
        documentStack.add(document)
        keysIterator.remove()
        return DocumentDecoder(nestedDocument, serializersModule)
    }

    // This method is called when the end of a nested structure is reached
    override fun endStructure(descriptor: SerialDescriptor) {
        // Pop the last (parent) document off the documentStack, as we're done deserializing the nested structure
        if (documentStack.isNotEmpty()) {
            documentStack.removeLast()
        }
    }
}
