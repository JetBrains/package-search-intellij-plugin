package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.externalSystem.model.Key
import com.jetbrains.packagesearch.plugin.core.utils.NioPathSerializer
import java.nio.file.Path
import kotlinx.serialization.Serializable

@Serializable
data class PackageSearchGradleModel(
    @Serializable(with = NioPathSerializer::class) val projectDir: Path,
    val configurations: List<Configuration>,
    val declaredRepositories: List<DeclaredRepository>,
    val isJavaApplied: Boolean,
    val isAmperApplied: Boolean,
    val isKotlinAndroidApplied: Boolean,
    val isKotlinMultiplatformApplied: Boolean,
    val projectIdentityPath: String,
    val projectName: String,
    val rootProjectName: String,
    @Serializable(with = NioPathSerializer::class) val buildFilePath: Path?,
    @Serializable(with = NioPathSerializer::class) val rootProjectPath: Path,
    val gradleVersion: String,
) {

    companion object {
        val DATA_NODE_KEY: Key<PackageSearchGradleModel> =
            Key.create(PackageSearchGradleModel::class.java, 100)
    }

    @Serializable
    data class DeclaredRepository(
        val url: String,
        val name: String?,
    )

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