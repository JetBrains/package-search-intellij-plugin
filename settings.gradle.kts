@file:Suppress("UnstableApiUsage")

rootProject.name = "pkgs-v2"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    plugins {
        val kotlinVersion = "1.8.20"
        kotlin("jvm") version kotlinVersion
        kotlin("multiplatform") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("org.jetbrains.intellij") version "1.13.3"
        id("org.jetbrains.compose") version "1.4.0"
        id("com.google.devtools.ksp") version "1.8.20-1.0.11"
        id("org.gradlex.java-ecosystem-capabilities") version "1.1"
    }
}

include(
    ":plugin",
    ":plugin:core",
    ":plugin:maven",
    ":plugin:gradle",
    ":plugin:gradle:tooling",
    ":package-search-api-client",
    ":package-search-api-mock-server",
    ":gradle-metadata-schema"
)

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

includeBuild("package-search-version-utils") {
    dependencySubstitution {
        substitute(module("org.jetbrains.packagesearch:package-search-version-utils")).using(project(":"))
    }
}
