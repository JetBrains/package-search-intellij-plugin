package org.jetbrains.packagesearch.plugin.gradle

import kotlinx.serialization.Serializable

@Serializable
data class PackageSearchGradleModel(
    val projectDir: String,
    val configurations: List<Configuration>,
    val repositories: List<String>,
    val isKotlinJsApplied: Boolean,
    val isKotlinJvmApplied: Boolean,
    val isKotlinAndroidApplied: Boolean,
    val isKotlinMultiplatformApplied: Boolean
) {

    @Serializable
    data class Configuration(
        val name: String,
        val dependencies: List<Dependency>,
    )

    @Serializable
    data class Dependency(
        val groupId: String,
        val artifactId: String,
        val version: String,
    )
}