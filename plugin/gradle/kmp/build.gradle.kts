@file:Suppress("UnstableApiUsage")

plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    id(packageSearchCatalog.plugins.idea.gradle.plugin.base)
    alias(packageSearchCatalog.plugins.dokka)
    id(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `maven-publish`
}

dependencies {

    intellijPlatform {
        intellijIdeaCommunity(INTELLIJ_VERSION)
        bundledPlugins(
            "org.jetbrains.plugins.gradle",
            "org.jetbrains.idea.gradle.dsl"
        )
    }

    api(projects.plugin.gradle)
    api(projects.kmpModifier)
    compileOnly(projects.plugin.gradle.tooling)
}
