package org.jetbrains.packagesearch.plugin.core.nitrite.serialization

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