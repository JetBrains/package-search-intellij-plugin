package com.jetbrains.packagesearch.plugin.tests

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import kotlinx.serialization.Serializable

@Serializable
data class SerializableIdentity(
    val group: String,
    val path: String,
)


fun PackageSearchModule.Identity.toSerializable() = SerializableIdentity(group, path)