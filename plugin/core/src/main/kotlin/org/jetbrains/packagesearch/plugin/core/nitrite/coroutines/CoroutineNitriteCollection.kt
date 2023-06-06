package org.jetbrains.packagesearch.plugin.core.nitrite.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.NitriteCollection
import org.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI

class CoroutineNitriteCollection(
    val synchronous: NitriteCollection,
    override val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoroutineWrapper() {

    suspend fun createIndex(field: String, indexOptions: IndexOptions) =
        dispatch { synchronous.createIndex(field, indexOptions) }

}