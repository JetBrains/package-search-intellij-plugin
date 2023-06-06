plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.idea.gradle.plugin)
    alias(packageSearchCatalog.plugins.compose.desktop)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    id("packagesearch")
}

dependencies {
    implementation(packageSearchCatalog.jewel.foundation)
    api(packageSearchCatalog.packagesearch.api.models)
    api(packageSearchCatalog.packagesearch.version.utils)
    implementation(projects.plugin.maven)
    implementation(projects.plugin.gradle)
    implementation(projects.plugin.core)
}