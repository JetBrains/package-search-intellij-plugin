@file:Suppress("UnstableApiUsage")

import org.gradle.toolchains.foojay.FoojayToolchainResolver

rootProject.name = "packagesearch-intellij-plugin"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    versionCatalogs {
        create("packageSearchCatalog") {
            from(files("./gradle/packagesearch.versions.toml"))
        }
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

val isSubmoduleNotInit = sequenceOf("jewel", "package-search-api-models", "package-search-version-utils")
    .map { file(it) }
    .any { !it.isDirectory || it.listFiles()?.isEmpty() ?: true }

if (isSubmoduleNotInit) {
    logger.info("git submodules not check out. Initializing them...")
    exec { commandLine("git", "submodule", "init") }
    exec { commandLine("git", "submodule", "update") }
}

includeBuild("build-config")

includeBuild("jewel") {
    dependencySubstitution {
        substitute(module("org.jetbrains.jewel:foundation")).using(project(":foundation"))
        substitute(module("org.jetbrains.jewel:core")).using(project(":core"))
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

