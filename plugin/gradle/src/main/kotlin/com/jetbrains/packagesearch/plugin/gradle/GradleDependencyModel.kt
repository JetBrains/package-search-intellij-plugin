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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GradleDependencyModel

        if (groupId != other.groupId) return false
        if (artifactId != other.artifactId) return false
        if (version != other.version) return false
        if (configuration != other.configuration) return false

        return true
    }

    override fun hashCode(): Int {
        var result = groupId.hashCode()
        result = 31 * result + artifactId.hashCode()
        result = 31 * result + (version?.hashCode() ?: 0)
        result = 31 * result + configuration.hashCode()
        return result
    }


}