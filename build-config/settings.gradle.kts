@file:Suppress("UnstableApiUsage")

rootProject.name = "packagesearch-build-config"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("packageSearchCatalog") {
            from(files("../packagesearch.versions.toml"))
        }
    }
}
