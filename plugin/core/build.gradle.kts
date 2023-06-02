import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
    kotlin("plugin.serialization")
}

intellij {
    version.set("LATEST-EAP-SNAPSHOT")
    plugins.addAll("org.jetbrains.idea.maven")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.serialization.ExperimentalSerializationApi")
                optIn("kotlinx.serialization.InternalSerializationApi")
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("org.jetbrains.packagesearch.plugin.InternalAPI")
            }
        }
    }
}

dependencies {
    api(projects.packageSearchApiClient)
    api("org.jetbrains.packagesearch:package-search-api-models")
    api("org.dizitart:nitrite:3.4.4") {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.squareup.okhttp3")
        exclude(group = "org.slf4j")
    }
    implementation("io.ktor:ktor-client-cio-jvm:2.3.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation(kotlin("test-junit5"))
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
}

