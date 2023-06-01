package org.jetbrains.packagesearch.gradlemetadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GradleMetadata(
    val formatVersion: String,
    val component: Component,
    val createdBy: CreatedBy? = null,
    val variants: List<Variant>? = null
)

@Serializable
data class Component(
    val group: String,
    val module: String,
    val version: String,
    val url: String? = null,
    val attributes: Map<String, String>? = null
)

@Serializable
data class CreatedBy(
    val gradle: Gradle
)

@Serializable
data class Gradle(
    val version: String,
    val buildId: String? = null
)

@Serializable
data class Variant(
    val name: String,
    val attributes: Map<String, String>? = null,
    @SerialName("available-at") val availableAt: AvailableAt? = null,
    val dependencies: List<Dependency>? = null,
    val dependencyConstraints: List<DependencyConstraint>? = null,
    val files: List<File>? = null
)

@Serializable
data class AvailableAt(
    val url: String,
    val group: String,
    val module: String,
    val version: String
)

@Serializable
data class Dependency(
    val group: String,
    val module: String,
    val version: Version
)

@Serializable
data class Version(
    val requires: String
)

@Serializable
data class DependencyConstraint(
    val group: String,
    val module: String,
    val version: Version
)

@Serializable
data class File(
    val name: String,
    val url: String,
    val size: Int,
    val sha512: String,
    val sha256: String,
    val sha1: String,
    val md5: String
)
