package org.jetbrains.packagesearch.plugin.core.nitrite.serialization

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.dizitart.no2.Document

sealed class NitriteDocumentFormat(
    val configuration: NitriteDocumentFormatConfiguration = NitriteDocumentFormatConfiguration(),
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : SerialFormat {

    companion object Default : NitriteDocumentFormat()

    private val json = Json {
        encodeDefaults = configuration.encodeDefaults
        ignoreUnknownKeys = configuration.ignoreUnknownKeys
        isLenient = configuration.isLenient
        allowStructuredMapKeys = configuration.allowStructuredMapKeys
        prettyPrint = configuration.prettyPrint
        explicitNulls = configuration.explicitNulls
        prettyPrintIndent = configuration.prettyPrintIndent
        coerceInputValues = configuration.coerceInputValues
        useArrayPolymorphism = configuration.useArrayPolymorphism
        classDiscriminator = configuration.classDiscriminator
        allowSpecialFloatingPointValues = configuration.allowSpecialFloatingPointValues
        useAlternativeNames = configuration.useAlternativeNames
        namingStrategy = configuration.namingStrategy
    }

    fun <T> encodeToDocument(serializer: SerializationStrategy<T>, element: T): Document {
        val jsonElement = json.encodeToJsonElement(serializer, element)
        val result = Document()
        when (jsonElement) {
            is JsonObject -> result.writeJson(jsonElement)
            is JsonArray -> jsonElement.forEachIndexed { index, value ->
                result[index.toString()] = value.asDocument()
            }

            is JsonPrimitive -> when {
                jsonElement.isString -> result["0"] = jsonElement.content
                else -> result["0"] = jsonElement.parseJsonPrimitive()
            }

            JsonNull -> error("Should not be null!")
        }
        return result
    }

    fun <T : Any> decodeFromDocument(serializer: DeserializationStrategy<T>, document: Document): T {
        val jsonElement =
            JsonObject(document.mapValues { (_, value) -> value.asJsonElement() })
        return json.decodeFromJsonElement(serializer, jsonElement)
    }
}

public class NitriteDocumentFormatBuilder internal constructor(nitriteDocumentFormat: NitriteDocumentFormat) {
    /**
     * Specifies whether default values of Kotlin properties should be encoded.
     * `false` by default.
     */
    public var encodeDefaults: Boolean = nitriteDocumentFormat.configuration.encodeDefaults

    /**
     * Specifies whether `null` values should be encoded for nullable properties and must be present in JSON object
     * during decoding.
     *
     * When this flag is disabled properties with `null` values without default are not encoded;
     * during decoding, the absence of a field value is treated as `null` for nullable properties without a default value.
     *
     * `true` by default.
     */
    @ExperimentalSerializationApi
    public var explicitNulls: Boolean = nitriteDocumentFormat.configuration.explicitNulls

    /**
     * Specifies whether encounters of unknown properties in the input JSON
     * should be ignored instead of throwing [SerializationException].
     * `false` by default.
     */
    public var ignoreUnknownKeys: Boolean = nitriteDocumentFormat.configuration.ignoreUnknownKeys

    /**
     * Removes JSON specification restriction (RFC-4627) and makes parser
     * more liberal to the malformed input. In lenient mode quoted boolean literals,
     * and unquoted string literals are allowed.
     *
     * Its relaxations can be expanded in the future, so that lenient parser becomes even more
     * permissive to invalid value in the input, replacing them with defaults.
     *
     * `false` by default.
     */
    public var isLenient: Boolean = nitriteDocumentFormat.configuration.isLenient

    /**
     * Enables structured objects to be serialized as map keys by
     * changing serialized form of the map from JSON object (key-value pairs) to flat array like `[k1, v1, k2, v2]`.
     * `false` by default.
     */
    public var allowStructuredMapKeys: Boolean = nitriteDocumentFormat.configuration.allowStructuredMapKeys

    /**
     * Specifies whether resulting JSON should be pretty-printed.
     *  `false` by default.
     */
    public var prettyPrint: Boolean = nitriteDocumentFormat.configuration.prettyPrint

    /**
     * Specifies indent string to use with [prettyPrint] mode
     * 4 spaces by default.
     * Experimentality note: this API is experimental because
     * it is not clear whether this option has compelling use-cases.
     */
    @ExperimentalSerializationApi
    public var prettyPrintIndent: String = nitriteDocumentFormat.configuration.prettyPrintIndent

    /**
     * Enables coercing incorrect JSON values to the default property value in the following cases:
     *   1. JSON value is `null` but property type is non-nullable.
     *   2. Property type is an enum type, but JSON value contains unknown enum member.
     *
     * `false` by default.
     */
    public var coerceInputValues: Boolean = nitriteDocumentFormat.configuration.coerceInputValues

    /**
     * Switches polymorphic serialization to the default array format.
     * This is an option for legacy JSON format and should not be generally used.
     * `false` by default.
     */
    public var useArrayPolymorphism: Boolean = nitriteDocumentFormat.configuration.useArrayPolymorphism

    /**
     * Name of the class descriptor property for polymorphic serialization.
     * "type" by default.
     */
    public var classDiscriminator: String = nitriteDocumentFormat.configuration.classDiscriminator

    /**
     * Removes JSON specification restriction on
     * special floating-point values such as `NaN` and `Infinity` and enables their serialization and deserialization.
     * When enabling it, please ensure that the receiving party will be able to encode and decode these special values.
     * `false` by default.
     */
    public var allowSpecialFloatingPointValues: Boolean =
        nitriteDocumentFormat.configuration.allowSpecialFloatingPointValues

    /**
     * Specifies whether Json instance makes use of [JsonNames] annotation.
     *
     * Disabling this flag when one does not use [JsonNames] at all may sometimes result in better performance,
     * particularly when a large count of fields is skipped with [ignoreUnknownKeys].
     * `true` by default.
     */
    public var useAlternativeNames: Boolean = nitriteDocumentFormat.configuration.useAlternativeNames

    /**
     * Specifies [JsonNamingStrategy] that should be used for all properties in classes for serialization and deserialization.
     *
     * `null` by default.
     *
     * This strategy is applied for all entities that have [StructureKind.CLASS].
     */
    @ExperimentalSerializationApi
    public var namingStrategy: JsonNamingStrategy? = nitriteDocumentFormat.configuration.namingStrategy

    /**
     * Module with contextual and polymorphic serializers to be used in the resulting [Json] instance.
     *
     * @see SerializersModule
     * @see Contextual
     * @see Polymorphic
     */
    public var serializersModule: SerializersModule = nitriteDocumentFormat.serializersModule

    internal fun build() = NitriteDocumentFormatConfiguration(
        encodeDefaults, ignoreUnknownKeys, isLenient,
        allowStructuredMapKeys, prettyPrint, explicitNulls, prettyPrintIndent,
        coerceInputValues, useArrayPolymorphism,
        classDiscriminator, allowSpecialFloatingPointValues, useAlternativeNames,
        namingStrategy
    )
}

fun NitriteDocumentFormat(
    from: NitriteDocumentFormat = NitriteDocumentFormat.Default,
    builderAction: NitriteDocumentFormatBuilder.() -> Unit = {}
): NitriteDocumentFormat {
    val builder = NitriteDocumentFormatBuilder(from).apply(builderAction)
    val configuration = builder.build()
    return NitriteDocumentFormatImpl(
        configuration,
        builder.serializersModule
    )
}

private class NitriteDocumentFormatImpl(
    configuration: NitriteDocumentFormatConfiguration,
    module: SerializersModule
) : NitriteDocumentFormat(configuration, module)

fun Any?.asJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is Document -> JsonObject(mapValues { (_, value) -> value.asJsonElement() })
    is Iterable<*> -> JsonArray(map { it.asJsonElement() })
    is Array<*> -> JsonArray(map { it.asJsonElement() })
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is UByte -> JsonPrimitive(this)
    is UInt -> JsonPrimitive(this)
    is ULong -> JsonPrimitive(this)
    is UShort -> JsonPrimitive(this)
    else -> error("Unknown type while decoding")
}

fun Document.writeJson(jsonElement: JsonObject): Unit =
    jsonElement.forEach { (key, value) ->
        put(key, value.asDocument())
    }

private fun JsonElement.asDocument(): Any? = when (this) {
    is JsonArray -> map { it.asDocument() }
    is JsonObject -> Document().also { it.writeJson(this) }
    JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        else -> parseJsonPrimitive()
    }
}

private fun JsonPrimitive.parseJsonPrimitive() =
    content.toIntOrNull()
        ?: content.toDoubleOrNull()
        ?: content.toBooleanStrictOrNull()
        ?: error("\"$content\" is not a number or a boolean.")

public class NitriteDocumentFormatConfiguration internal constructor(
    public val encodeDefaults: Boolean = false,
    public val ignoreUnknownKeys: Boolean = true,
    public val isLenient: Boolean = false,
    public val allowStructuredMapKeys: Boolean = false,
    public val prettyPrint: Boolean = false,
    public val explicitNulls: Boolean = true,
    public val prettyPrintIndent: String = "    ",
    public val coerceInputValues: Boolean = false,
    public val useArrayPolymorphism: Boolean = false,
    public val classDiscriminator: String = "type",
    public val allowSpecialFloatingPointValues: Boolean = false,
    public val useAlternativeNames: Boolean = true,
    public val namingStrategy: JsonNamingStrategy? = null,
)
