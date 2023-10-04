@file:Suppress("UnstableApiUsage")

import org.jetbrains.packagesearch.gradle.pkgsSpace

plugins {
    `version-catalog`
    `maven-publish`
    `build-config`
}

allprojects {
    group = "org.jetbrains.packagesearch"
    version = project.properties["pluginVersion"]?.toString() ?: "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
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