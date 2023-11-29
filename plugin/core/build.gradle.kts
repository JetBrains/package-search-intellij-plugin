@file:Suppress("UnstableApiUsage")

import org.jetbrains.packagesearch.gradle.GeneratePackageSearchObject


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
        artifactId = "packagesearch-plugin-core"
    }
}

dependencies {
    api(projects.nitrite)
    sourceElements(projects.nitrite)
    api(packageSearchCatalog.packagesearch.api.client)
    api(packageSearchCatalog.nitrite) {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.squareup.okhttp3")
        exclude(group = "org.slf4j")
    }
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.api)
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.engine)
    testImplementation(packageSearchCatalog.kotlinx.coroutines.test)
    testImplementation(kotlin("test-junit5"))
}

val generatedDir: Provider<Directory> = layout.buildDirectory.dir("generated/main/kotlin")

kotlin.sourceSets.main {
    kotlin.srcDirs(generatedDir)
}

val pkgsPluginId: String by project

tasks {
    withType<Test> {
        environment("DB_PATH", layout.buildDirectory.file("tests/cache.db").get().asFile.absolutePath)
    }
    val generatePluginDataSources by registering(GeneratePackageSearchObject::class) {
        pluginId = pkgsPluginId
        outputDir = generatedDir
        packageName = "com.jetbrains.packagesearch.plugin.core"
    }
    sourcesJar {
        dependsOn(generatePluginDataSources)
    }
    dokkaHtml {
        dependsOn(generatePluginDataSources)
    }
    compileKotlin {
        dependsOn(generatePluginDataSources)
    }
}