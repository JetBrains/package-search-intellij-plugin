package org.jetbrains.packagesearch.plugin.core.nitrite.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.dizitart.no2.Nitrite
import org.dizitart.no2.NitriteCollection
import org.jetbrains.packagesearch.plugin.core.nitrite.coroutine
import org.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormat
import org.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI

class CoroutineNitrite internal constructor(
    val synchronous: Nitrite,
    val documentFormat: NitriteDocumentFormat,
    override val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoroutineWrapper() {
    suspend inline fun <reified T : Any> getRepository(key: String): CoroutineObjectRepository<T> =
        dispatch { synchronous.getRepository(key, T::class.java).coroutine(documentFormat) }

    suspend fun getCollection(key: String) =
        dispatch { synchronous.getCollection(key).coroutine() }
}

fun NitriteCollection.coroutine() =
    CoroutineNitriteCollection(this)
