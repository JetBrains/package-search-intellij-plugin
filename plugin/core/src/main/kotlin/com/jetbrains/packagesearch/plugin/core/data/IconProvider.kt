package com.jetbrains.packagesearch.plugin.core.data

import kotlinx.serialization.Serializable

interface IconProvider {

    @Serializable
    data class Icon(val lightIconPath: String, val darkIconPath: String = lightIconPath)

    object Icons {
        val MAVEN = Icon("icons/repositoryLibraryLogo.svg")
        val GRADLE = Icon("icons/expui/gradle.svg", "icons/expui/gradle_dark.svg")
        val COCOAPODS = Icon("icons/cocoapods.svg")
        val NPM = Icon("icons/npm.svg")
        val KOTLIN = Icon("org/jetbrains/kotlin/idea/icons/kotlin.svg")
    }

    val icon: Icon
}