package com.jetbrains.packagesearch.plugin.core.nitrite.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.NitriteCollection
import org.dizitart.no2.WriteResult
import org.dizitart.no2.objects.filters.ObjectFilters

class CoroutineNitriteCollection(
    val synchronous: NitriteCollection,
    override val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoroutineWrapper() {

    suspend fun createIndex(field: String, indexOptions: IndexOptions) =
        dispatch { synchronous.createIndex(field, indexOptions) }

    suspend fun removeAll(): WriteResult =
        dispatch { synchronous.remove(ObjectFilters.ALL) }

}