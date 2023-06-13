plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.idea.gradle.plugin)
    alias(packageSearchCatalog.plugins.compose.desktop)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    id("build-config")
}

dependencies {
    implementation(packageSearchCatalog.jewel.foundation)
    implementation(packageSearchCatalog.packagesearch.api.models)
    compileOnly(packageSearchCatalog.kotlinx.serialization.core)
    implementation(projects.plugin.maven)
    implementation(projects.plugin.gradle)
    implementation(projects.plugin.core)
}
