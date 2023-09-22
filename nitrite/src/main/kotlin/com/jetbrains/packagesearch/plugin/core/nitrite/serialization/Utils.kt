package com.jetbrains.packagesearch.plugin.core.nitrite.serialization

import kotlinx.serialization.serializer
import org.dizitart.no2.Document

fun Document.getDocument(key: String) = get(key) as Document

inline fun <reified T: Any> NitriteDocumentFormat.encodeToDocument(element: T): Document =
    encodeToDocument(serializer<T>(), element)

inline fun <reified T: Any> NitriteDocumentFormat.decodeFromDocument(document: Document) =
    decodeFromDocument(serializer<T>(), document)