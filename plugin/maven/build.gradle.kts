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
        bundledPlugin("org.jetbrains.idea.maven")
    }
    api(projects.plugin.core)
    api(packageSearchCatalog.packagesearch.build.systems.models)
}
