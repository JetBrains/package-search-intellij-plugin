@file:Suppress("UnstableApiUsage")

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version packageSearchCatalog.versions.kotlin.get()
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
    implementation("com.squareup:kotlinpoet:1.14.2")
    implementation("io.github.pdvrieze.xmlutil:serialization:0.86.2")
}
