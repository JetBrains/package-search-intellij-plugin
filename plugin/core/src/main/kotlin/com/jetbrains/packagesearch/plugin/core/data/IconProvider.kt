package com.jetbrains.packagesearch.plugin.core.data

interface IconProvider {

    object Icons {
        const val MAVEN = "icons/maven.svg"
        const val GRADLE = "icons/gradle.svg"
        const val COCOAPODS = "icons/cocoapods.svg"
        const val NPM = "icons/npm.svg"
    }

    val iconPath: String
}