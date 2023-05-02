@file:Suppress("UnstableApiUsage")

rootProject.name = "pkgs-v2"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    plugins {
        kotlin("jvm") version "1.8.20"
        id("org.jetbrains.intellij") version "1.13.3"
        id("org.jetbrains.compose") version "1.4.0"
    }
}

include(":plugin")

includeBuild("jewel") {
    dependencySubstitution {
        substitute(module("org.jetbrains.jewel:foundation")).using(project(":foundation"))
    }
}

includeBuild("package-search-api-models") {
    dependencySubstitution {
        substitute(module("org.jetbrains.packagesearch:package-search-api-models")).using(project(":"))
    }
}
