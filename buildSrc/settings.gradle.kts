@file:Suppress("UnstableApiUsage")

rootProject.name = "buildSrc"

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
