@file:Suppress("UnstableApiUsage")

rootProject.name = "packagesearch-intellij-plugin"

pluginManagement {
    includeBuild("packagesearch-version-catalog")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

val isSubmoduleNotInit =
    sequenceOf("jewel", "package-search-api-models", "package-search-version-utils", "packagesearch-build-config")
    .map { file(it) }
    .any { !it.isDirectory || it.listFiles()?.isEmpty() ?: true }

if (isSubmoduleNotInit) {
    logger.info("git submodules not check out. Initializing them...")
    exec { commandLine("git", "submodule", "init") }
    exec { commandLine("git", "submodule", "update") }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("packageSearchCatalog") {
            from(files("./packagesearch-version-catalog/packagesearch.versions.toml"))
        }
    }
}

include(
    ":plugin",
    ":plugin:core",
    ":plugin:maven",
    ":plugin:gradle",
    ":plugin:gradle:tooling",
    ":packagesearch-api-client",
    ":packagesearch-api-mock-server",
    ":gradle-metadata-schema"
)

includeBuild("packagesearch-build-config")

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

includeBuild("package-search-version-utils") {
    dependencySubstitution {
        substitute(module("org.jetbrains.packagesearch:packagesearch-version-utils")).using(project(":"))
    }
}
