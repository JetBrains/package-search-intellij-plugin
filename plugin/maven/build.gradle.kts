import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
    kotlin("plugin.serialization")
    id("packagesearch")
}

intellij {
    plugins.addAll("org.jetbrains.idea.maven")
}

dependencies {
    api(projects.plugin.core)
}