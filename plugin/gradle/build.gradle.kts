import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
    kotlin("plugin.serialization")
    id("packagesearch")
}

intellij {
    plugins.addAll("org.jetbrains.plugins.gradle")
}

dependencies {
    implementation(projects.plugin.gradle.tooling)
    implementation(projects.plugin.core)
}