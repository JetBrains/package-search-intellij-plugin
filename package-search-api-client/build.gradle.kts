import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    alias(packageSearchCatalog.plugins.dokka)
    id("packagesearch")
    `maven-publish`
}



dependencies {
    api(packageSearchCatalog.packagesearch.api.models)
    implementation(packageSearchCatalog.ktor.client.cio)
    implementation(packageSearchCatalog.ktor.client.content.negotiation)
    implementation(packageSearchCatalog.ktor.serialization.kotlinx.json)
    implementation(packageSearchCatalog.kotlinx.serialization.json)
}