@file:Suppress("UnstableApiUsage")

plugins {
    id(packageSearchCatalog.plugins.kotlin.jvm)
    id(packageSearchCatalog.plugins.idea.gradle.plugin)
    id(packageSearchCatalog.plugins.dokka)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `build-config`
    `maven-publish`
}

packagesearch {
    publication {
        isEnabled = true
        artifactId = "packagesearch-plugin-kmp"
    }
}

intellij {
    plugins.add("org.jetbrains.plugins.gradle")
    if (packagesearch.intellijVersion.get().isAndroidStudio) {
        plugins.add("android")
    } else {
        plugins.add("org.jetbrains.idea.gradle.dsl")
    }
}

dependencies {
    api(projects.plugin.gradle)
    api(projects.kmpModifier)
    sourceElements(projects.plugin.gradle)
    sourceElements(projects.kmpModifier)
}