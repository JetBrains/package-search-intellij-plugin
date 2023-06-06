package org.jetbrains.packagesearch.plugin.core.nitrite.serialization

fun NitriteDocumentSerializer(builderAction: NitriteDocumentSerializerBuilder.() -> Unit) =
    NitriteDocumentSerializerBuilder().apply(builderAction).build()