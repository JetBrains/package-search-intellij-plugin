@file:Suppress("JSON_FORMAT_REDUNDANT")

package com.jetbrains.packagesearch.plugin.tests

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TestResult<T>(
    val value: T? = null,
    val error: SerializableThrowable? = null,
)

@Serializable
data class SerializableThrowable(
    val typeName: String? = null,
    val message: String,
    val stackTrace: List<String>,
    val cause: SerializableThrowable? = null,
) {
    override fun toString() =
        Json { prettyPrint = true }.encodeToString(this)
}