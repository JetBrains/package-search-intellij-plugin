package com.jetbrains.packagesearch.plugin.core.nitrite.coroutines

import com.jetbrains.packagesearch.plugin.core.nitrite.DocumentPathBuilder
import com.jetbrains.packagesearch.plugin.core.nitrite.asKotlin
import com.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormat
import java.io.Closeable
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.dizitart.no2.FindOptions
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.NitriteId
import org.dizitart.no2.RemoveOptions
import org.dizitart.no2.WriteResult
import org.dizitart.no2.event.ChangeListener
import org.dizitart.no2.event.ChangeType
import org.dizitart.no2.objects.ObjectFilter
import org.dizitart.no2.objects.ObjectRepository
import org.dizitart.no2.objects.filters.ObjectFilters

@RequiresOptIn("This API is internal and you should not use it.")
annotation class InternalAPI

class CoroutineObjectRepository<T : Any> @InternalAPI constructor(
    val synchronous: ObjectRepository<T>,
    val type: KType,
    private val documentFormat: NitriteDocumentFormat,
    override val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoroutineWrapper(), Closeable by synchronous {

    data class Change<T>(val changeType: ChangeType, val changedItems: Flow<T>)
    data class Item<T>(val changeTimestamp: Instant, val changeType: ChangeType, val item: T)

    val documentCollection
        get() = CoroutineNitriteCollection(synchronous.documentCollection)

    private val indexMutex = Mutex()

    suspend fun findById(id: Long): T? = dispatch { synchronous.getById(NitriteId.createId(id)) }
    suspend fun findById(id: Int): T? = findById(id.toLong())

    suspend fun find(filter: ObjectFilter? = null, findOptions: FindOptions? = null): Flow<T> =
        withContext(dispatcher) {
            when {
                filter != null && findOptions != null -> synchronous.find(filter, findOptions).asFlow()
                filter != null -> synchronous.find(filter).asFlow()
                else -> synchronous.find().asFlow()
            }.flowOn(dispatcher)
        }

    fun changes(): Flow<Change<Item<T>>> = channelFlow {
        val listener = ChangeListener { trySend(it.asKotlin(documentFormat, type)) }
        synchronous.register(listener)
        awaitClose { synchronous.deregister(listener) }
    }

    suspend fun insert(items: Array<T>): WriteResult =
        dispatch { synchronous.insert(items) }

    suspend fun update(filter: ObjectFilter, update: T, upsert: Boolean = false): WriteResult =
        dispatch { synchronous.update(filter, update, upsert) }

    suspend fun removeAll(): WriteResult = dispatch {
        try {
            synchronous.remove(ObjectFilters.ALL)
        } catch (_: NullPointerException) {
            object : WriteResult {
                override fun iterator(): MutableIterator<NitriteId> = object : MutableIterator<NitriteId> {
                    override fun hasNext(): Boolean = false
                    override fun next(): NitriteId = throw NoSuchElementException()
                    override fun remove() {}
                }

                override fun getAffectedCount(): Int = 0
            }
        }
    }

    suspend fun remove(filter: ObjectFilter, removeOptions: RemoveOptions? = null): WriteResult =
        dispatch {
            if (removeOptions != null) synchronous.remove(filter, removeOptions)
            else synchronous.remove(filter)
        }

    suspend fun remove(element: T): WriteResult =
        dispatch { synchronous.remove(element) }

    suspend fun createIndex(
        indexOptions: IndexOptions,
        path: DocumentPathBuilder,
    ) = indexMutex.withLock {
        val field = path.build()
        dispatch { if (!synchronous.hasIndex(field)) synchronous.createIndex(field, indexOptions) }
    }

    suspend fun createIndex(
        indexOptions: IndexOptions,
        path: KProperty<*>,
    ) = indexMutex.withLock {
        dispatch { if (!synchronous.hasIndex(path.name)) synchronous.createIndex(path.name, indexOptions) }
    }

}