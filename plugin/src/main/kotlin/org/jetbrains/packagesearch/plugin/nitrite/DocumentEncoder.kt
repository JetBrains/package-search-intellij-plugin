package org.jetbrains.packagesearch.plugin.nitrite

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.SerializersModule
import org.dizitart.no2.Document
import kotlin.properties.Delegates

class DocumentEncoder(override val serializersModule: SerializersModule) : AbstractEncoder() {

    // The result map that will store the serialized data
    val result = Document()

    // A stack to keep track of nested maps during serialization
    private val resultStack = mutableListOf<Document>()

    // A variable to hold the current key (property name) being serialized
    private var currentName: String by Delegates.notNull()

    // This method is called to encode primitive values
    override fun encodeValue(value: Any) {
        // If the resultStack is empty, we're at the top level, so add the value to the result map
        if (resultStack.isEmpty()) {
            result[currentName] = value
        } else {
            // Otherwise, we're in a nested map, so add the value to the last (current) map in the stack
            resultStack.last()[currentName] = value
        }
    }

    // This method is called before a property is about to be serialized
    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        // Set the current key to the property name based on the index in the descriptor
        currentName = descriptor.getElementName(index)
        return true
    }

    // This method is called when a nested structure (e.g., map, list, or another data class) is encountered
    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        // Create a new nested result map for the nested structure
        val nestedResult = Document()

        // If the resultStack is empty, we're at the top level, so add the nested map to the result map
        if (resultStack.isEmpty()) {
            result[currentName] = nestedResult
        } else {
            // Otherwise, we're in a nested map, so add the nested map to the last (current) map in the stack
            resultStack.last()[currentName] = nestedResult
        }

        // Push the new nested map onto the resultStack
        resultStack.add(nestedResult)
        return this
    }

    // This method is called when the end of a nested structure is reached
    override fun endStructure(descriptor: SerialDescriptor) {
        // Pop the last (current) nested map off the resultStack, as we're done serializing it
        if (resultStack.isNotEmpty()) {
            resultStack.removeLast()
        }
    }
}