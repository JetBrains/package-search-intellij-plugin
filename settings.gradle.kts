@file:Suppress("UnstableApiUsage")

rootProject.name = "packagesearch-intellij-plugin"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    versionCatalogs {
        create("packageSearchCatalog") {
            from(files("packagesearch.versions.toml"))
        }
    }
}

include(
    ":plugin",
    ":plugin:core",
    ":plugin:maven",
    ":plugin:gradle",
    ":plugin:gradle:tooling",
    ":packagesearch-api-mock-server",
)

includeBuild("build-config")

includeBuild("jewel") {
    dependencySubstitution {
        substitute(module("org.jetbrains.jewel:foundation")).using(project(":foundation"))
        substitute(module("org.jetbrains.jewel:core")).using(project(":core"))
    }
}

includeBuild("package-search-api-models") {
    dependencySubstitution {
        substitute(module("org.jetbrains.packagesearch:packagesearch-api-models")).using(project(":"))
    }
}
