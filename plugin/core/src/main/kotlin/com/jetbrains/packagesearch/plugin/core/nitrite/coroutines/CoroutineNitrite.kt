package com.jetbrains.packagesearch.plugin.core.nitrite.coroutines

import com.jetbrains.packagesearch.plugin.core.nitrite.coroutine
import com.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormat
import java.io.Closeable
import org.dizitart.no2.Nitrite
import org.dizitart.no2.NitriteCollection

class CoroutineNitrite internal constructor(
    val synchronous: Nitrite,
    val documentFormat: NitriteDocumentFormat,
) : Closeable by synchronous {
    inline fun <reified T : Any> getRepository(key: String): CoroutineObjectRepository<T> =
        synchronous.getRepository(key, T::class.java).coroutine(documentFormat)

    fun getCollection(key: String) =
        synchronous.getCollection(key).coroutine()
}

fun NitriteCollection.coroutine() =
    CoroutineNitriteCollection(this)
