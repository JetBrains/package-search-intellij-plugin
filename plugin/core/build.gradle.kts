import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.idea.gradle.plugin)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    alias(packageSearchCatalog.plugins.packagesearch.build.config)
    `maven-publish`
}

packagesearch {
    publication {
        artifactId.set("packagesearch-plugin-core")
    }
}

dependencies {
    api(projects.packagesearchApiClient)
    api(packageSearchCatalog.packagesearch.api.models)
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
