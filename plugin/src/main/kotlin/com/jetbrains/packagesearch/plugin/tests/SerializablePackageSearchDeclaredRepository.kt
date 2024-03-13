package com.jetbrains.packagesearch.plugin.tests

import kotlinx.serialization.Serializable

@Serializable
data class SerializablePackageSearchDeclaredRepository(
    val name: String? = null,
    val url: String,
)
