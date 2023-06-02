package org.jetbrains.packagesearch.plugin.core.nitrite

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule
import org.dizitart.no2.Document

internal sealed interface Deserializable {
    fun keysIterator(): Iterator<String>
    operator fun get(i: String): Any?
    @JvmInline
    value class Doc(val document: Document) : Deserializable {
        override fun keysIterator() = document.keys.iterator()
        override fun get(i: String): Any? = document[i]
    }
    @JvmInline
    value class List(val list: kotlin.collections.List<*>) : Deserializable {
        override fun keysIterator() =
            list.indices.map { it.toString() }.iterator()

        override fun get(i: String) = list[i.toInt()]
    }
}

internal class DocumentDecoder(
    private val deserializable: Deserializable,
    override val serializersModule: SerializersModule
) : AbstractDecoder() {

    private val keysIterator: Iterator<String> = deserializable.keysIterator()
    private var currentKey: String? = null

    override fun decodeValue() =
        currentKey?.let { deserializable[it] } ?: throw SerializationException("currentKey is null")

    override fun decodeNotNullMark() =
        currentKey?.let { deserializable[it] } != null

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (!keysIterator.hasNext()) return CompositeDecoder.DECODE_DONE
        currentKey = keysIterator.next()
        return currentKey?.let { descriptor.getElementIndex(it) } ?: CompositeDecoder.UNKNOWN_NAME
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

        return DocumentDecoder(nestedElement, serializersModule)
    }
}
