@file:OptIn(ExperimentalSerializationApi::class)

package org.jetbrains.packagesearch.gradle.json

import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.inputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

object NioPathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("path", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.absolutePathString())
    override fun deserialize(decoder: Decoder) = Path.of(decoder.decodeString())
}
typealias SerializablePath = @Serializable(with = NioPathSerializer::class) Path

interface FileSystemDistributed {
    val directory: SerializablePath
}

internal const val ATTRIBUTES_DIR_NAME = "attributes"

internal fun FileSystemDistributed.loadAttributes(ref: Int?): Map<String, String> =
    when (ref) {
        null -> emptyMap()
        else -> Json.decodeFromStream(directory.resolve("$ATTRIBUTES_DIR_NAME/$ref.json").inputStream())
    }

internal fun FileSystemDistributed.loadDependencyResult(ref: Int): SerializableDependencyResult =
    Json.decodeFromStream(directory.resolve("${SerializableDependencyResult.DIR_NAME}/$ref.json").inputStream())

internal fun FileSystemDistributed.loadComponentSelector(ref: Int): SerializableComponentSelector =
    Json.decodeFromStream(directory.resolve("${SerializableComponentSelector.DIR_NAME}/$ref.json").inputStream())

internal fun FileSystemDistributed.loadResolvedComponentResult(ref: Int): SerializableResolvedComponentResult =
    Json.decodeFromStream(directory.resolve("${SerializableResolvedComponentResult.DIR_NAME}/$ref.json").inputStream())

internal fun FileSystemDistributed.loadResolvedVariantResult(ref: Int): SerializableResolvedVariantResult =
    Json.decodeFromStream(directory.resolve("${SerializableResolvedVariantResult.DIR_NAME}/$ref.json").inputStream())

internal fun FileSystemDistributed.loadComponentSelectionReason(ref: Int): SerializableComponentSelectorReason =
    Json.decodeFromStream(directory.resolve("${SerializableComponentSelectorReason.DIR_NAME}/$ref.json").inputStream())

internal fun FileSystemDistributed.loadComponentIdentifier(ref: Int): SerializableComponentIdentifier =
    Json.decodeFromStream(directory.resolve("${SerializableComponentIdentifier.DIR_NAME}/$ref.json").inputStream())

internal fun FileSystemDistributed.loadModuleIdentifier(ref: Int): SerializableModuleIdentifier =
    Json.decodeFromStream(directory.resolve("${SerializableModuleIdentifier.DIR_NAME}/$ref.json").inputStream())

internal fun FileSystemDistributed.loadCapability(ref: Int): SerializableCapability =
    Json.decodeFromStream(directory.resolve("${SerializableCapability.DIR_NAME}/$ref.json").inputStream())

internal fun FileSystemDistributed.loadFailure(ref: Int): SerializableThrowable =
    Json.decodeFromStream(directory.resolve("${SerializableThrowable.DIR_NAME}/$ref.json").inputStream())

internal fun FileSystemDistributed.loadComponentSelectionDescriptor(ref: Int): SerializableComponentSelectionDescriptor =
    Json.decodeFromStream(directory.resolve("${SerializableComponentSelectionDescriptor.DIR_NAME}/$ref.json").inputStream())

internal fun FileSystemDistributed.loadVersionConstraint(ref: Int): SerializableVersionConstraint =
    Json.decodeFromStream(directory.resolve("${SerializableVersionConstraint.DIR_NAME}/$ref.json").inputStream())