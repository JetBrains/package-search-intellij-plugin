package com.jetbrains.packagesearch.plugin.core.data

interface IconProvider {

    object Icons {
        const val MAVEN = "icons/repositoryLibraryLogo.svg"
        const val GRADLE_LIGHT = "icons/expui/gradle.svg"
        const val GRADLE_DARK = "icons/expui/gradle_dark.svg"
        const val COCOAPODS = "icons/cocoapods.svg"
        const val NPM = "icons/npm.svg"
        const val KOTLIN = "org/jetbrains/kotlin/idea/icons/kotlin.svg"
    }

    val lightIconPath: String

    val darkIconPath: String
        get() = lightIconPath
}