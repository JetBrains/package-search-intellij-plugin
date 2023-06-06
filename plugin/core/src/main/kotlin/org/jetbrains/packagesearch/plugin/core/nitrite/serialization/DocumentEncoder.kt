package org.jetbrains.packagesearch.plugin.core.nitrite.serialization

import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.SerializersModule
import org.dizitart.no2.Document

class DocumentEncoder(override val serializersModule: SerializersModule) : AbstractEncoder() {

    // The result map that will store the serialized data
    val result = Document()

    // A stack to keep track of nested maps during serialization
    private val resultStack = mutableListOf<Any>()

    // A variable to hold the current key (property name) being serialized
    // Initialize it with a default value to avoid "should be initialized before get" error
    private var currentName: String? = null

    // This method is called to encode primitive values
    override fun encodeValue(value: Any) {
        currentName?.let { name ->
            // If the resultStack is empty, we're at the top level, so add the value to the result map
            if (resultStack.isEmpty()) {
                result[name] = value
            } else {
                // Otherwise, we're in a nested map, so add the value to the last (current) map in the stack
                when(val last = resultStack.last()) {
                    is MutableList<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        (last as MutableList<Any>).add(value)
                    }
                    is Document -> last[name] = value
                    else -> error("Encoding stack contains error")
                }
            }
        }
    }

    override fun encodeNull() {
        if (currentName == "_id") return
        currentName?.let { name ->
            // If the resultStack is empty, we're at the top level, so add the value to the result map
            if (resultStack.isEmpty()) {
                result[name] = null
            } else {
                // Otherwise, we're in a nested map, so add the value to the last (current) map in the stack
                when(val last = resultStack.last()) {
                    is MutableList<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        (last as MutableList<Any?>).add(null)
                    }
                    is Document -> last[name] = null
                    else -> error("Encoding stack contains error")
                }
            }
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
        currentName?.let { name ->

            // Create a new nested result map for the nested structure
            val nestedResult = when (descriptor.kind) {
                StructureKind.LIST -> mutableListOf<Any>()
                else -> Document()
            }

            // If the resultStack is empty, we're at the top level, so add the nested map to the result map
            if (resultStack.isEmpty()) {
                result[name] = nestedResult
            } else {
                // Otherwise, we're in a nested map, so add the nested map to the last (current) map in the stack
                when(val last = resultStack.last()) {
                    is MutableList<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        (last as MutableList<Any?>).add(nestedResult)
                    }
                    is Document -> last[name] = nestedResult
                    else -> error("Encoding stack contains error")
                }
            }

            // Push the new nested map onto the resultStack
            resultStack.add(nestedResult)
        }
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