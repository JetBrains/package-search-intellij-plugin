import org.jetbrains.packagesearch.gradle.PackageSearchExtension
import org.jetbrains.packagesearch.gradle.pkgsSpace
import org.jetbrains.packagesearch.gradle.withType

plugins {
    `version-catalog`
    `maven-publish`
    `build-config`
}

allprojects {
    group = "org.jetbrains.packagesearch"
    version = "1.0.0-SNAPSHOT"

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
        from(files("packagesearch.versions.toml"))
    }
}

publishing {
    repositories {
        pkgsSpace(project)
    }
    publications {
        create<MavenPublication>("versionCatalog") {
            from(components["versionCatalog"])
            artifactId = "packagesearch-version-catalog"
        }
    }
}