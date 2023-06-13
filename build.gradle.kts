import org.jetbrains.packagesearch.gradle.PackageSearchExtension
import org.jetbrains.packagesearch.gradle.pkgsSpace
import org.jetbrains.packagesearch.gradle.withType

plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm) apply false
    alias(packageSearchCatalog.plugins.kotlin.multiplatform) apply false
    alias(packageSearchCatalog.plugins.dokka) apply false
    alias(packageSearchCatalog.plugins.idea.gradle.plugin) apply false
    `version-catalog`
    `maven-publish`
    id("build-config")
}

allprojects {
    group = "org.jetbrains.packagesearch"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases")
    }
    extensions.withType<PackageSearchExtension> {
        intellijVersion.set("2023.2-SNAPSHOT")
    }
}

catalog {
    versionCatalog {
        from(files("packageSearch.versions.toml"))
    }
}

publishing {
    repositories {
        pkgsSpace()
    }
    publications {
        create<MavenPublication>("versionCatalog") {
            from(components["versionCatalog"])
            artifactId = "packagesearch-version-catalog"
        }
    }
}