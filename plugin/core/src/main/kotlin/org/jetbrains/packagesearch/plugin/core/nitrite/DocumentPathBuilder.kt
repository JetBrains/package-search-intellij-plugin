package org.jetbrains.packagesearch.plugin.core.nitrite

import kotlin.reflect.KProperty

class DocumentPathBuilder {
    private val stringBuilder = mutableListOf<String>()

    operator fun div(other: KProperty<*>): DocumentPathBuilder {
        stringBuilder.add(other.getSerializableName())
        return this
    }

    operator fun div(other: String): DocumentPathBuilder {
        stringBuilder.add(other)
        return this
    }

    fun append(segment: KProperty<*>): DocumentPathBuilder {
        stringBuilder.add(segment.getSerializableName())
        return this
    }

    fun append(segment: String): DocumentPathBuilder {
        stringBuilder.add(segment)
        return this
    }

    fun build() = stringBuilder.joinToString(".")
}