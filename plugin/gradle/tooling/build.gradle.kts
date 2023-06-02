import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    java
    kotlin("jvm") apply false
    id("org.jetbrains.intellij")
}

intellij {
    version.set("LATEST-EAP-SNAPSHOT")
    plugins.addAll("org.jetbrains.plugins.gradle")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
