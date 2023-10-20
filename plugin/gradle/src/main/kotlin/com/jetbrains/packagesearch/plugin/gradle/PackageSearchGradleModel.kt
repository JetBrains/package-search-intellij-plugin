package com.jetbrains.packagesearch.plugin.gradle

import com.jetbrains.packagesearch.plugin.core.utils.NioPathSerializer
import java.nio.file.Path
import kotlinx.serialization.Serializable

@Serializable
data class PackageSearchGradleModel(
    @Serializable(with = NioPathSerializer::class) val projectDir: Path,
    val configurations: List<Configuration>,
    val repositories: List<String>,
    val isKotlinJvmApplied: Boolean,
    val isKotlinAndroidApplied: Boolean,
    val isKotlinMultiplatformApplied: Boolean,
    val projectIdentityPath: String,
    val projectName: String,
    val rootProjectName: String,
    @Serializable(with = NioPathSerializer::class) val buildFilePath: Path?,
    @Serializable(with = NioPathSerializer::class) val rootProjectPath: Path
) {

    @Serializable
    data class Configuration(
        val name: String,
        val dependencies: List<Dependency>,
        val canBeResolved: Boolean,
        val canBeDeclared: Boolean,
        val canBeConsumed: Boolean,
    )

    @Serializable
    data class Dependency(
        val groupId: String,
        val artifactId: String,
        val version: String,
    )
}