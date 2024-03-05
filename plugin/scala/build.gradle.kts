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
        artifactId = "packagesearch-plugin-scala"
    }
}

intellij {
    plugins.add("org.intellij.scala:2024.1.7")
}

dependencies {
    api(projects.plugin.core)
    sourceElements(projects.plugin.core)
    implementation(packageSearchCatalog.packagesearch.build.systems.models)
}