@file:Suppress("UnstableApiUsage")

rootProject.name = "packagesearch-intellij-plugin"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
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

if (file(".gitsubmoduleinit").run { !exists() }) {
    logger.lifecycle("Initializing git submodules")
    exec {
        commandLine("git", "submodule", "update", "--init")
    }
    file(".gitsubmoduleinit").writeText("stub")
}

include(
    ":nitrite",
    ":plugin",
    ":plugin:core",
    ":plugin:gradle",
    ":plugin:gradle:base",
    ":plugin:gradle:kmp",
    ":plugin:gradle:tooling",
    ":plugin:maven",
    ":kmp-modifier",
)

includeBuild("package-search-api-models") {
    dependencySubstitution {
        substitute(module("org.jetbrains.packagesearch:packagesearch-api-models")).using(project(":"))
        substitute(module("org.jetbrains.packagesearch:packagesearch-http-models")).using(project(":http"))
        substitute(module("org.jetbrains.packagesearch:packagesearch-api-client")).using(project(":http:client"))
        substitute(module("org.jetbrains.packagesearch:packagesearch-build-systems-models")).using(project(":build-systems"))
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
            file("build-scan-url.txt").writeText(buildScanUri.toString())
        }
    }
}
