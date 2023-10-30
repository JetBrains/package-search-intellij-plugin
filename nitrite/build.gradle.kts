@file:Suppress("UnstableApiUsage")

plugins {
    id(packageSearchCatalog.plugins.kotlin.jvm)
    id(packageSearchCatalog.plugins.dokka)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `build-config`
    `maven-publish`
}

packagesearch {
    publication {
        isEnabled = true
        artifactId = "packagesearch-nitrite"
    }
}

dependencies {
    api(packageSearchCatalog.kotlinx.serialization.json)
    api(packageSearchCatalog.kotlinx.coroutines.core)
    api(packageSearchCatalog.kotlinx.datetime)
    api(packageSearchCatalog.nitrite) {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.squareup.okhttp3")
        exclude(group = "org.slf4j")
    }
    implementation(kotlin("reflect"))
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.api)
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.engine)
    testImplementation(packageSearchCatalog.kotlinx.coroutines.test)
    testImplementation(packageSearchCatalog.packagesearch.build.systems.models)
    testImplementation(packageSearchCatalog.packagesearch.api.models)
    testImplementation(packageSearchCatalog.packagesearch.http.models)
    testImplementation(kotlin("test-junit5"))
}

kotlin {
    sourceSets {
        all {
            languageSettings {
                optIn("com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.InternalAPI")
            }
        }
    }
}

tasks {
    withType<Test> {
        environment("DB_PATH", layout.buildDirectory.file("tests/cache.db").get().asFile.absolutePath)
    }
}