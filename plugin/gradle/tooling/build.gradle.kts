@file:Suppress("UnstableApiUsage")

plugins {
    java
    id(packageSearchCatalog.plugins.idea.gradle.plugin.base)
    `maven-publish`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
    targetCompatibility = JavaVersion.VERSION_1_8
    sourceCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(INTELLIJ_VERSION)
        bundledPlugin("org.jetbrains.plugins.gradle")
    }
}