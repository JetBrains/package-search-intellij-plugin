package org.jetbrains.packagesearch.plugin.nitrite

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.dizitart.no2.*
import org.dizitart.no2.objects.ObjectFilter
import org.dizitart.no2.objects.ObjectRepository
import kotlin.reflect.KClass

abstract class CoroutineWrapper {
    protected abstract val dispatcher: CoroutineDispatcher

    suspend fun <T> dispatch(action: suspend CoroutineScope.() -> T) =
        withContext(dispatcher, action)
}

class CoroutineNitrite(
    val synchronous: Nitrite,
    val coroutineScope: CoroutineScope,
    override val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CoroutineWrapper() {
    suspend inline fun <reified T: Any> getRepository(key: String): CoroutineObjectRepository<T> =
        dispatch { synchronous.getRepository(key, T::class.java).coroutine(coroutineScope) }
}

inline fun <reified T : Any> CoroutineObjectRepository(
    synchronous: ObjectRepository<T>,
    coroutineScope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) = CoroutineObjectRepository(synchronous, T::class, coroutineScope, dispatcher)

class CoroutineObjectRepository<T : Any>(
    val synchronous: ObjectRepository<T>,
    val type: KClass<T>,
    coroutineScope: CoroutineScope,
    override val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoroutineWrapper() {

    val documentCollection
        get() = CoroutineNitriteCollection(synchronous.documentCollection)

    private val changesChannel = Channel<Unit>()
    private val changesFlow = changesChannel
        .consumeAsFlow()
        .shareIn(coroutineScope, SharingStarted.Eagerly)

    suspend fun find(filter: ObjectFilter? = null, findOptions: FindOptions? = null) =
        when {
            filter != null && findOptions != null -> synchronous.find(filter, findOptions).asFlow()
            filter != null -> synchronous.find(filter).asFlow()
            else -> synchronous.find().asFlow()
        }.flowOn(dispatcher)
            .toList()

    fun changes(filter: ObjectFilter? = null, findOptions: FindOptions? = null, initial: Boolean = true) =
        flow {
            if (initial) emit(find(filter, findOptions))
            changesFlow.collect { emit(find(filter, findOptions)) }
        }

    suspend fun insert(items: Array<T>): WriteResult {
        val result = dispatch { synchronous.insert(items) }
        changesChannel.send(Unit)
        return result
    }

    suspend fun createIndex(field: String, indexOptions: IndexOptions) {
        dispatch { synchronous.createIndex(field, indexOptions) }
    }

}


class CoroutineNitriteCollection(
    val synchronous: NitriteCollection,
    override val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoroutineWrapper() {

    suspend fun createIndex(field: String, indexOptions: IndexOptions) = dispatch {
        synchronous.createIndex(field, indexOptions)
    }

}