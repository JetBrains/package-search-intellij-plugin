@file:Suppress("UnstableApiUsage")

plugins {
    id(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `build-config`
    application
}

packagesearch {
    publication {
        isEnabled = false
    }
}

application {
    mainClass = "org.jetbrains.packagesearch.server.ServerKt"
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
    api(packageSearchCatalog.ktor.server.call.logging)
    api(packageSearchCatalog.ktor.server.cio)
    api(packageSearchCatalog.ktor.server.compression)
    api(packageSearchCatalog.ktor.server.content.negotiation)
    api(packageSearchCatalog.logback.classic)
    api(projects.apiMock)
    testImplementation(kotlin("test-junit5"))
    testImplementation(packageSearchCatalog.junit.jupiter.api)
    testImplementation(packageSearchCatalog.ktor.server.test.host)
    testImplementation(packageSearchCatalog.commons.codec)
    testImplementation(packageSearchCatalog.packagesearch.api.client)
    testImplementation(packageSearchCatalog.kotlinx.coroutines.test)
    testCompileOnly(packageSearchCatalog.junit.jupiter.engine)
}

tasks {
    withType<Test> {
        testLogging.showStandardStreams = true
    }
}