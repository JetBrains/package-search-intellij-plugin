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
    plugins.addAll(
        "org.jetbrains.kotlin",
        "org.jetbrains.plugins.gradle",
        "org.jetbrains.idea.gradle.dsl"
    )
}

dependencies {
    compileOnly(projects.plugin.gradle.tooling)
    sourceElements(projects.plugin.gradle.tooling)
    api(projects.plugin.core)
    sourceElements(projects.plugin.core)
    sourceElements(projects.plugin.core)
    api(packageSearchCatalog.kotlinx.serialization.core)
}
