package com.jetbrains.packagesearch.plugin.core.nitrite

import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import com.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormat
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.serializer
import org.dizitart.no2.event.ChangeInfo
import org.dizitart.no2.objects.ObjectRepository

inline fun <reified T : Any> CoroutineObjectRepository(
    synchronous: ObjectRepository<T>,
    documentFormat: NitriteDocumentFormat,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) = CoroutineObjectRepository(
    synchronous,
    typeOf<T>(),
    documentFormat,
    dispatcher
)

@Suppress("UNCHECKED_CAST")
fun <T: Any> ChangeInfo.asKotlin(documentFormat: NitriteDocumentFormat, type: KType) =
    CoroutineObjectRepository.Change(
        changeType,
        changedItems.map {
            val serializer =
                documentFormat.serializersModule.serializer(type) as KSerializer<T>
            CoroutineObjectRepository.Item(
                Instant.fromEpochMilliseconds(it.changeTimestamp),
                it.changeType,
                documentFormat.decodeFromDocument(serializer, it.document)
            )
        }
    )

operator fun KProperty<*>.div(other: KProperty<*>) =
    DocumentPathBuilder().append(this@div).append(other)

operator fun KProperty<*>.div(other: String) =
    DocumentPathBuilder().append(this@div).append(other)

operator fun String.div(other: KProperty<*>) =
    DocumentPathBuilder().append(this@div).append(other)

internal fun KProperty<*>.getSerializableName() =
    annotations.filterIsInstance<SerialName>()
        .firstOrNull()
        ?.value
        ?: name

