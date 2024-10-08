@file:Suppress("UnstableApiUsage")

import java.lang.System.getenv
import kotlin.math.max
import org.jetbrains.kotlin.util.prefixIfNot
import org.jetbrains.kotlin.util.suffixIfNot


plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.dokka)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `maven-publish`
}

dependencies {
    api(packageSearchCatalog.kotlinx.serialization.core)
    api(packageSearchCatalog.packagesearch.api.client)
    api(packageSearchCatalog.potassium.nitrite)
    api(packageSearchCatalog.nitrite)
    api(packageSearchCatalog.nitrite.mvstore.adapter)

    testImplementation(kotlin("test-junit5"))
    testImplementation(packageSearchCatalog.ktor.client.mock)
    testImplementation(packageSearchCatalog.junit.jupiter.api)
    testImplementation(packageSearchCatalog.junit.jupiter.params)
    testImplementation(packageSearchCatalog.kotlinx.coroutines.test)
    testImplementation(packageSearchCatalog.assertk)
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.engine)
    testImplementation(packageSearchCatalog.logback.classic)
}

tasks {
    test {
        val cacheDir = layout.buildDirectory.dir("tests/cache")
        environment("CACHES", cacheDir.map { it.asFile.absolutePath }.get())
        doFirst {
            val cacheDirectory = cacheDir.get().asFile
            cacheDirectory.deleteRecursively()
            cacheDirectory.mkdirs()
        }
    }
}
