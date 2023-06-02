package org.jetbrains.packagesearch.plugin.core.nitrite

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
    private var currentKey: String? = null

    // Iterator to go through the keys of the current document
    private var keysIterator: Iterator<String> = document.keys.iterator()

    // This method is called to determine the next element index to decode
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        // Check if there are no more keys left in the iterator
        if (!keysIterator.hasNext()) return CompositeDecoder.DECODE_DONE

        // Set the current key to the next key in the iterator
        currentKey = keysIterator.next()

        // Return the index of the current key in the descriptor
        // If currentKey is null, return unknown index
        return currentKey?.let { descriptor.getElementIndex(it) } ?: CompositeDecoder.UNKNOWN_NAME
    }

    // This method is called to decode a primitive value
    override fun decodeValue(): Any {
        // Get the value corresponding to the current key from the current document
        // If currentKey is null, throw an exception
        return currentKey?.let { document[it] } ?: throw SerializationException("currentKey is null")
    }

    // This method is called when a nested structure (e.g., document, list, or another data class) is encountered
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        // If the currentKey hasn't been set yet, try to get it from the iterator
        if (currentKey == null && keysIterator.hasNext()) {
            currentKey = keysIterator.next()
        }

        // Get the nested document corresponding to the current key
        // If currentKey is null, throw an exception
        val nestedDocument = currentKey?.let { document[it] as? Document }
            ?: throw SerializationException("currentKey is null or the value is not a Document")

        // Push the current document onto the documentStack and set the nested document as the new current document
        documentStack.add(document)
        keysIterator = nestedDocument.keys.iterator()
        return DocumentDecoder(nestedDocument, serializersModule)
    }

    // This method is called when the end of a nested structure is reached
    override fun endStructure(descriptor: SerialDescriptor) {
        // Pop the last (parent) document off the documentStack, as we're done deserializing the nested structure
        if (documentStack.isNotEmpty()) {
            documentStack.removeLast()
        }

        // Reset the current key and the keys iterator for the next level of the document
        if (documentStack.isNotEmpty()) {
            keysIterator = documentStack.last().keys.iterator()
            currentKey = null
        }
    }
}
