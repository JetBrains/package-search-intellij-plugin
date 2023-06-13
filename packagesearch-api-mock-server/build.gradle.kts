import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    alias(packageSearchCatalog.plugins.packagesearch.build.config)
    application
}

application {
    mainClass.set("org.jetbrains.packagesearch.server.ServerKt")
}

dependencies {
    api(packageSearchCatalog.kotlinx.datetime)
    api(packageSearchCatalog.kotlinx.serialization.json)
    api(packageSearchCatalog.ktor.client.cio)
    api(packageSearchCatalog.ktor.client.content.negotiation)
    api(packageSearchCatalog.ktor.serialization.kotlinx.json)
    api(packageSearchCatalog.ktor.server.call.logging)
    api(packageSearchCatalog.ktor.server.cio)
    api(packageSearchCatalog.ktor.server.content.negotiation)
    api(packageSearchCatalog.logback.classic)
    api(packageSearchCatalog.packagesearch.api.models)
    api(projects.gradleMetadataSchema)
}