@file:Suppress("UnstableApiUsage")

plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.idea.gradle.plugin)
    alias(packageSearchCatalog.plugins.dokka)
    id(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `build-config`
    `maven-publish`
}

packagesearch {
    publication {
        isEnabled = true
        artifactId = "packagesearch-plugin-maven"
    }
}

intellij {
    plugins.add("org.jetbrains.idea.maven")
    plugins.add("com.intellij.java")
}

dependencies {
    api(projects.plugin.core)
    sourceElements(projects.plugin.core)
    implementation(packageSearchCatalog.packagesearch.build.systems.models)
}