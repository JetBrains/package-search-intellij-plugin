@file:Suppress("UnstableApiUsage")

rootProject.name = "packagesearch-intellij-plugin"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    versionCatalogs {
        create("packageSearchCatalog") {
            from(files("/gradle/packagesearch.versions.toml"))
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
includeBuild("build-config")

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
