@file:Suppress("UnstableApiUsage")

plugins {
    `kotlin-dsl`
}

group = "org.jetbrains.packagesearch"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(libs.poet.kotlin)
    implementation(libs.flexmark)
    implementation(libs.gradle.intellij.platform.plugin)
}
