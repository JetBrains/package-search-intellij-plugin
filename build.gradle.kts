@file:Suppress("UnstableApiUsage")

import java.lang.System.getenv
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.packagesearch.gradle.pkgsSpace

plugins {
    `version-catalog`
    `maven-publish`
    `build-config`
}

allprojects {
    group = "org.jetbrains.packagesearch"
    val baseVersion = "233.0.0-SNAPSHOT"
    version = when (val ref = getenv("GITHUB_REF")) {
        null -> baseVersion
        else -> when {
            ref.startsWith("refs/tags/") -> ref.removePrefix("refs/tags/")
            else -> baseVersion
        }
    }

    repositories {
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://packages.jetbrains.team/maven/p/kpm/public")
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