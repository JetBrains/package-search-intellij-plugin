@file:Suppress("UnstableApiUsage")

plugins {
    id(packageSearchCatalog.plugins.kotlin.jvm)
    id(packageSearchCatalog.plugins.dokka)
    id(packageSearchCatalog.plugins.idea.gradle.plugin)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `build-config`
    `maven-publish`
}

packagesearch {
    publication {
        isEnabled = true
        artifactId = "packagesearch-plugin-gradle-dependencies-report"
    }
}

intellij {
    plugins.add("org.jetbrains.plugins.gradle")
}

dependencies {
    api(packageSearchCatalog.kotlinx.serialization.core)
}
