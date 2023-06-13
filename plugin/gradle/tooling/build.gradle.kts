import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion

plugins {
    java
    alias(packageSearchCatalog.plugins.idea.gradle.plugin)
    alias(packageSearchCatalog.plugins.packagesearch.build.config)
}

packagesearch {
    java {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

intellij {
    plugins.add("org.jetbrains.plugins.gradle")
}