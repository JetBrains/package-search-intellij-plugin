@file:Suppress("UnstableApiUsage")

rootProject.name = "pkgs-v2"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    plugins {
        kotlin("jvm") version "1.8.20"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":plugin")

includeBuild("jewel") {

}
