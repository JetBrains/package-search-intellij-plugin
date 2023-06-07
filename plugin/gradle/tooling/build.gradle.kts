import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion

plugins {
    java
    alias(packageSearchCatalog.plugins.idea.gradle.plugin)
    id("packagesearch")
}

packagesearch {
    javaVersion.set(JavaLanguageVersion.of(8))
}

intellij {
    plugins.add("org.jetbrains.plugins.gradle")
}