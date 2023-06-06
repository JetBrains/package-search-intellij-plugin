import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
    kotlin("plugin.serialization")
    id("packagesearch")
    `maven-publish`
}

packagesearch {
//    publicationArtifactId.set("packagesearch-plugin-core")
}

dependencies {
    api(projects.packageSearchApiClient)
    api(packageSearchCatalog.packagesearch.api.models)
    api(packageSearchCatalog.nitrite) {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.squareup.okhttp3")
        exclude(group = "org.slf4j")
    }
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.api)
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.engine)
    testImplementation(kotlin("test-junit5"))
}
