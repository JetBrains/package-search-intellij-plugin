package org.jetbrains.packagesearch.plugin.core.nitrite.serialization

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule
import org.dizitart.no2.Document

internal class DocumentDecoder(
    private val deserializable: Deserializable,
    override val serializersModule: SerializersModule,
    private val ignoreUnknownKeys: Boolean
) : AbstractDecoder() {

    private val keysIterator: Iterator<String> = deserializable.keysIterator()
    private var currentKey: String? = null

    override fun decodeValue() =
        currentKey?.let { deserializable[it] } ?: throw SerializationException("currentKey is null")

    override fun decodeNotNullMark() =
        currentKey?.let { deserializable[it] } != null

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (true) {
            if (!keysIterator.hasNext()) return CompositeDecoder.DECODE_DONE
            val nextKey = keysIterator.next()
            val index = descriptor.getElementIndex(nextKey)
            if (index == CompositeDecoder.UNKNOWN_NAME && ignoreUnknownKeys) continue
            else {
                currentKey = nextKey
                return index
            }
        }
    }

    /**
     * `{A: 42, C: {D}}`
     *
     * ```
     * val d1 = d1.beginStructure(A)
     *      d1.decodeElementIndex() -> 0
     *      val x = d1.decodeValue -> 42
     *      d1.decodeElementIndex() -> 1
     *      val y = DSerializer.deserialize(d1)
     *          val d2 = d1.beginStructure(D.serializer().serialDescriptor)
     *          d2.decodeElementIndex
     *          ...
     *          d2.endStructure
     *      d1.decodeElementIndex() -> DECODE_DONE
     *      d1.endStructure
     * ```
     */
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (currentKey == null) {
            return this
        }

        val nestedElement = currentKey?.let {
            when (val element = deserializable[it]) {
                is List<*> -> Deserializable.List(element)
                is Document -> Deserializable.Doc(element)
                else -> error("Wrong type, document or list expected")
            }
        } ?: throw SerializationException("currentKey is null or the value is not a Document")

        return DocumentDecoder(nestedElement, serializersModule, ignoreUnknownKeys)
    }
}
