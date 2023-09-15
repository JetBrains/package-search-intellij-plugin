package com.jetbrains.packagesearch.plugin.maven

import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes

class MavenDependencyModel(
    val groupId: String,
    val artifactId: String,
    val version: String?,
    val scope: String?,
    val indexes: DependencyDeclarationIndexes,
) {

    val packageId
        get() = "maven:$groupId:$artifactId"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MavenDependencyModel

        if (groupId != other.groupId) return false
        if (artifactId != other.artifactId) return false
        if (version != other.version) return false
        if (scope != other.scope) return false

        return true
    }

    override fun hashCode(): Int {
        var result = groupId.hashCode()
        result = 31 * result + artifactId.hashCode()
        result = 31 * result + (version?.hashCode() ?: 0)
        result = 31 * result + (scope?.hashCode() ?: 0)
        return result
    }
}