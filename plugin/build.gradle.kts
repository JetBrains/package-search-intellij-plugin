import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
}

intellij {
    version.set("2023.1.1")
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
    implementation(projects.packageSearchApiClient)
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${getKotlinPluginVersion()}")
    implementation("org.gradle:gradle-tooling-api:8.1.1")
    implementation("org.jetbrains.jewel:foundation") {
        exclude(group = "org.jetbrains.kotlinx")
    }
    api("org.jetbrains.packagesearch:package-search-api-models")
    api("org.jetbrains.packagesearch:package-search-version-utils")
    implementation("io.ktor:ktor-client-cio:2.3.0")
    api("org.dizitart:nitrite:3.4.4") {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.squareup.okhttp3")
        exclude(group = "org.slf4j")
    }
}