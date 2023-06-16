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
        artifactId.set("packagesearch-plugin-gradle")
    }
}

intellij {
    plugins.addAll("org.jetbrains.plugins.gradle")
}

dependencies {
    implementation(projects.plugin.gradle.tooling)
    implementation(projects.plugin.core)
    compileOnly(packageSearchCatalog.kotlinx.serialization.core)
}