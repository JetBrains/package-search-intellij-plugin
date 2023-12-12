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