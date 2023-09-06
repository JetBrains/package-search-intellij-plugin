package com.jetbrains.packagesearch.plugin.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.jetbrains.packagesearch.plugin.core.data.IconProvider.PathSourceType.ClasspathResources

interface IconProvider {

    object Icons {
        val MAVEN = ClasspathResources("icons/maven.svg")
        val GRADLE = ClasspathResources("icons/gradle.svg")
        val COCOAPODS = ClasspathResources("icons/cocoapods.svg")
        val NPM = ClasspathResources("icons/npm.svg")
    }

    val icon: PathSourceType

    @Serializable
    sealed interface PathSourceType {

        val path: String

        @JvmInline
        @Serializable
        @SerialName("classpath-resources")
        value class ClasspathResources(override val path: String) : PathSourceType

        @JvmInline
        @Serializable
        @SerialName("network")
        value class Network(override val path: String) : PathSourceType

        @JvmInline
        @Serializable
        @SerialName("platform")
        value class Platform(override val path: String) : PathSourceType

        @JvmInline
        @Serializable
        @SerialName("file")
        value class File(override val path: String) : PathSourceType
    }
}