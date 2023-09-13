package com.jetbrains.packagesearch.plugin.gradle

import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes

data class GradleDependencyModel(
    val groupId: String,
    val artifactId: String,
    val version: String?,
    val configuration: String,
    val indexes: DependencyDeclarationIndexes,
) {

    val packageId
        get() = "maven:$groupId:$artifactId"
}