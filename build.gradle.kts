@file:Suppress("UnstableApiUsage")

import java.lang.System.getenv
import org.jetbrains.packagesearch.gradle.getStringOrNull
import org.jetbrains.packagesearch.gradle.pkgsSpace

plugins {
    `version-catalog`
    `maven-publish`
    `build-config`
}

allprojects {
    group = "org.jetbrains.packagesearch"
    val baseVersion = "241.99999-SNAPSHOT"
    version = when (val ref = getenv("GITHUB_REF")) {
        null -> baseVersion
        else -> when {
            ref.startsWith("refs/tags/") -> ref.removePrefix("refs/tags/")
            else -> baseVersion
        }
    }

    repositories {
        google()
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://packages.jetbrains.team/maven/p/kpm/public")
        maven("https://www.jetbrains.com/intellij-repository/snapshots")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-sdk-nightly") {
            credentials {
                username = getenv("MAVEN_SPACE_USERNAME")
                    ?: project.extra.getStringOrNull("space.intellij.username")
                password = getenv("MAVEN_SPACE_PASSWORD")
                    ?: project.extra.getStringOrNull("space.intellij.password")
            }
        }
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