@file:Suppress("UnstableApiUsage")

import org.jetbrains.packagesearch.gradle.SupportedIntelliJVersion
import org.jetbrains.packagesearch.gradle.SupportedIntelliJVersion.*


plugins {
    java
    id(packageSearchCatalog.plugins.idea.gradle.plugin)
    `build-config`
    `maven-publish`
}

packagesearch {
    publication {
        isEnabled = true
        publishShadow = false
        artifactId = "packagesearch-plugin-gradle-tooling"
    }
    java {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

sourceSets {
    val intelliJName = when (packagesearch.intellijVersion.get()) {
        `232`, `AS-232` -> "232"
        else -> "233"
    }
    main {
        java {
            srcDir("src/${intelliJName}Main/java")
        }
    }
}

intellij {
    plugins.add("org.jetbrains.plugins.gradle")
}
