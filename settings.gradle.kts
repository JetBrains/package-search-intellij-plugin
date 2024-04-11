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
    ":nitrite",
    ":plugin",
    ":plugin:core",
    ":plugin:gradle",
    ":plugin:gradle:base",
    ":plugin:gradle:kmp",
    ":plugin:gradle:tooling",
    ":plugin:gradle:dependencies-report",
    ":plugin:maven",
    ":kmp-modifier",
)

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
