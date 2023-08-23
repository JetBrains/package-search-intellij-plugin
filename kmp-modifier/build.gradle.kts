@file:Suppress("UnstableApiUsage")

plugins {
    id(packageSearchCatalog.plugins.kotlin.jvm)
    id(packageSearchCatalog.plugins.idea.gradle.plugin)
    id(packageSearchCatalog.plugins.dokka)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `build-config`
    `maven-publish`
}
dependencies {
    implementation(packageSearchCatalog.kotlinx.serialization.core)
    implementation(packageSearchCatalog.kotlinx.serialization.protobuf)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(packageSearchCatalog.junit4.base)
    testRuntimeOnly(packageSearchCatalog.junit.vintage.engine)
}

packagesearch {
    publication {
        isEnabled = true
        artifactId = "kmp-dependency-modifier"
    }
}

intellij {
    plugins.addAll(
        "org.jetbrains.kotlin",
        "org.jetbrains.plugins.gradle",
        "org.jetbrains.idea.gradle.dsl"
    )
}