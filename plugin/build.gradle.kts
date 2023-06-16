plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.idea.gradle.plugin)
    alias(packageSearchCatalog.plugins.dokka)
    alias(packageSearchCatalog.plugins.compose.desktop)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    id("build-config")
    `maven-publish`
}

packagesearch {
    publication {
        isEnabled.set(true)
        artifactId.set("packagesearch-plugin")
    }
}

dependencies {
    implementation(packageSearchCatalog.jewel.foundation)
    implementation(packageSearchCatalog.packagesearch.api.models)
    compileOnly(packageSearchCatalog.kotlinx.serialization.core)
    implementation(projects.plugin.maven)
    implementation(projects.plugin.gradle)
    implementation(projects.plugin.core)
}
