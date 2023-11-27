@file:Suppress("UnstableApiUsage")

plugins {
    id(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `build-config`
}

packagesearch {
    publication {
        isEnabled = false
    }
}

dependencies {
    api(packageSearchCatalog.kotlinx.datetime)
    api(packageSearchCatalog.kotlinx.serialization.json)
    api(packageSearchCatalog.kotlinx.serialization.protobuf)
    api(packageSearchCatalog.ktor.client.cio)
    api(packageSearchCatalog.ktor.client.logging)
    api(packageSearchCatalog.ktor.client.content.negotiation)
    api(packageSearchCatalog.ktor.serialization.kotlinx.json)
    api(packageSearchCatalog.ktor.serialization.kotlinx.protobuf)
    api(packageSearchCatalog.packagesearch.http.models)
    api(packageSearchCatalog.packagesearch.build.systems.models)
}
