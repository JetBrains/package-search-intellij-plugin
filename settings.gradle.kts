@file:Suppress("UnstableApiUsage")

import java.lang.System.getenv
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform


rootProject.name = "packagesearch-intellij-plugin"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    id("com.gradle.enterprise") version "3.14.1"
    id("org.jetbrains.intellij.platform.settings") version "2.1.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    versionCatalogs {
        create("packageSearchCatalog") {
            from(files("packagesearch.versions.toml"))
        }
    }
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://packages.jetbrains.team/maven/p/kpm/public")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-sdk-nightly") {
            credentials {
                username = getenv("SPACE_INTELLIJ_NIGHTLIES_USERNAME")
                    ?: settings.extra.getStringOrNull("space.intellij.username")
                password = getenv("SPACE_INTELLIJ_NIGHTLIES_TOKEN")
                    ?: settings.extra.getStringOrNull("space.intellij.password")
            }
        }
        intellijPlatform {
            defaultRepositories()
        }
    }
}

include(
    ":plugin",
    ":plugin:core",
    ":plugin:utils",
    ":plugin:gradle",
    ":plugin:gradle:base",
    ":plugin:gradle:kmp",
    ":plugin:gradle:tooling",
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
            if (isCi)
                file("build-scan-url.txt").writeText(buildScanUri.toString())
        }
    }
}

fun ExtraPropertiesExtension.getStringOrNull(name: String): String? =
    when {
        has(name) -> get(name) as? String
        else -> null
    }