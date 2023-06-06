package org.jetbrains.packagesearch.plugin.core.nitrite.serialization

import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

class NitriteDocumentSerializerBuilder {
    var ignoreUnknownsKeys: Boolean = false
    var serializersModule: SerializersModule = EmptySerializersModule()

    fun build() = NitriteDocumentFormat(serializersModule, ignoreUnknownsKeys)
}