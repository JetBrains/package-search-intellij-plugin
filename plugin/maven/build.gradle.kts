plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.idea.gradle.plugin)
    alias(packageSearchCatalog.plugins.dokka)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    id("build-config")
    `maven-publish`
}

packagesearch {
    publication {
        isEnabled.set(true)
        artifactId.set("packagesearch-plugin-maven")
    }
}

intellij {
    plugins.addAll("org.jetbrains.idea.maven")
}

dependencies {
    api(projects.plugin.core)
    compileOnly(packageSearchCatalog.kotlinx.serialization.core)
}