plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.dokka)
    alias(packageSearchCatalog.plugins.idea.gradle.plugin)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    id("build-config")
    `maven-publish`
}

packagesearch {
    publication {
        isEnabled.set(true)
        artifactId.set("packagesearch-plugin-core")
    }
}

dependencies {
    api(packageSearchCatalog.packagesearch.api.client)
    api(packageSearchCatalog.nitrite) {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.squareup.okhttp3")
        exclude(group = "org.slf4j")
    }
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.api)
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.engine)
    testImplementation(kotlin("test-junit5"))
    compileOnly(packageSearchCatalog.kotlinx.serialization.core)
}
