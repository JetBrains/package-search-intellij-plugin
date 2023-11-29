@file:Suppress("UnstableApiUsage")

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

intellij {
    plugins.add("org.jetbrains.plugins.gradle")
}
