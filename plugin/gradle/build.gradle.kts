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
        artifactId = "packagesearch-plugin-gradle-core"
    }
}

intellij {
    plugins.addAll("org.jetbrains.plugins.gradle")
}

dependencies {
    api(projects.plugin.gradle.tooling)
    api(projects.plugin.core)
    api(packageSearchCatalog.kotlinx.serialization.core)
}

//con