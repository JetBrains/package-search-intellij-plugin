package com.jetbrains.packagesearch.plugin.tests

import kotlinx.serialization.Serializable

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
)