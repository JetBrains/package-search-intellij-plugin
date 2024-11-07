@file:Suppress("UnstableApiUsage")

import org.jetbrains.packagesearch.gradle.GeneratePackageSearchObject


plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.dokka)
    id(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    id(packageSearchCatalog.plugins.idea.gradle.plugin.base)
    `maven-publish`
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(INTELLIJ_VERSION)
        bundledPlugins(
            "org.jetbrains.idea.reposearch",
            "com.jetbrains.performancePlugin"
        )
    }
    api(packageSearchCatalog.kotlinx.serialization.core)
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.api)
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.engine)
    testImplementation(packageSearchCatalog.kotlinx.coroutines.test)
    testImplementation(kotlin("test-junit5"))
}

val generatedDir: Provider<Directory> = layout.buildDirectory.dir("generated/main/kotlin")

kotlin.sourceSets.main {
    kotlin.srcDirs(generatedDir)
}

tasks {
    withType<Test> {
        environment("DB_PATH", layout.buildDirectory.file("tests/cache.db").get().asFile.absolutePath)
    }
    val generatePluginDataSources by registering(GeneratePackageSearchObject::class) {
        group = "generate"
        pluginId = PACKAGE_SEARCH_PLUGIN_ID
        outputDir = generatedDir
        packageName = "com.jetbrains.packagesearch.plugin.core"
        databaseVersion = 3
    }
    compileKotlin {
        dependsOn(generatePluginDataSources)
    }
}