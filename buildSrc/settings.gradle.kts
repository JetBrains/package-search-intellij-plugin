@file:Suppress("UnstableApiUsage")

rootProject.name = "buildSrc"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../packagesearch.versions.toml"))
        }
    }
}
