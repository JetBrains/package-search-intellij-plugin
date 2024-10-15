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
            "org.jetbrains.kotlin",
            "org.jetbrains.plugins.gradle",
            "org.jetbrains.idea.gradle.dsl"
        )
    }


    api(packageSearchCatalog.kotlinx.serialization.protobuf)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(packageSearchCatalog.junit4.base)
    testRuntimeOnly(packageSearchCatalog.junit.vintage.engine)
}
