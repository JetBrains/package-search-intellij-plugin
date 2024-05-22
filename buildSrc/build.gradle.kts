@file:Suppress("UnstableApiUsage")

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version "1.9.20"
}

group = "org.jetbrains.packagesearch"
version = "1.0.0-SNAPSHOT"

gradlePlugin {
    plugins {
        create("packagesearch") {
            id = "build-config"
            displayName = "Package Search Build Configurations"
            implementationClass = "org.jetbrains.packagesearch.gradle.PackageSearchPlugin"
            description = "Package Search Build Configurations"
        }
    }
}

dependencies {
    implementation(packageSearchCatalog.kotlin.gradle.plugin)
    implementation(packageSearchCatalog.gradle.intellij.platform.plugin)
    implementation(packageSearchCatalog.dokka.gradle.plugin)
    implementation(packageSearchCatalog.foojay.resolver.gradle.plugin)
    implementation(packageSearchCatalog.detekt.gradle.plugin)
    implementation(packageSearchCatalog.kotlinter.gradle.plugin)
    implementation(packageSearchCatalog.shadow.gradle.plugin)
    implementation(packageSearchCatalog.kotlinx.serialization.json)
    implementation(packageSearchCatalog.poet.kotlin)
    implementation(packageSearchCatalog.xmlutils.serialization)
    implementation(packageSearchCatalog.flexmark)
}
