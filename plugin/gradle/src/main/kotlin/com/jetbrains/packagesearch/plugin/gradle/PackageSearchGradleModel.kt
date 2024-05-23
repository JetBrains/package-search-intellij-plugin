package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.externalSystem.model.Key
import com.jetbrains.packagesearch.plugin.core.utils.SerializablePath
import kotlinx.serialization.Serializable

@Serializable
data class PackageSearchGradleModel(
    val projectDir: SerializablePath,
    val configurations: List<Configuration>,
    val declaredRepositories: List<DeclaredRepository>,
    val isJavaApplied: Boolean,
    val isAmperApplied: Boolean,
    val isKotlinAndroidApplied: Boolean,
    val isKotlinMultiplatformApplied: Boolean,
    val projectIdentityPath: String,
    val projectName: String,
    val rootProjectName: String,
    val buildFilePath: SerializablePath?,
    val rootProjectPath: SerializablePath,
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