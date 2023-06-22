@file:Suppress("UnstableApiUsage")

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
