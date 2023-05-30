import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    java
    kotlin("jvm") apply false
    id("org.jetbrains.intellij")
}

intellij {
    version.set("2023.1.1")
    plugins.addAll("org.jetbrains.plugins.gradle")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_6
    targetCompatibility = JavaVersion.VERSION_1_6
}
