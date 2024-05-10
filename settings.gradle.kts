@file:Suppress("UnstableApiUsage")

rootProject.name = "packagesearch-intellij-plugin"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    id("com.gradle.enterprise") version "3.14.1"
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
    ":plugin:utils",
    ":plugin:gradle",
    ":plugin:gradle:base",
    ":plugin:gradle:kmp",
    ":plugin:gradle:tooling",
    ":plugin:maven",
    ":kmp-modifier",
)

includeBuild("nitrite-java") {
    dependencySubstitution {
        substitute(module("org.dizitart:nitrite-java")).using(project(":nitrite"))
        substitute(module("org.dizitart:potassium-nitrite")).using(project(":potassium-nitrite"))
        substitute(module("org.dizitart:nitrite-mvstore-adapter")).using(project(":nitrite-mvstore-adapter"))
    }
}

val isCi
    get() = System.getenv("CI") == "true"

gradleEnterprise {
    buildScan {
        server = "https://ge.labs.jb.gg/"
        accessKey = System.getenv("GRADLE_ENTERPRISE_KEY")
            ?: extra.properties["gradleEnterpriseAccessKey"]?.toString()
        publishAlwaysIf(isCi)
        buildScanPublished {
            if (isCi)
                file("build-scan-url.txt").writeText(buildScanUri.toString())
        }
    }
}
