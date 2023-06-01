import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
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
    api("org.jetbrains.packagesearch:package-search-api-models")
    api("io.ktor:ktor-server-cio:2.3.0")
    api("io.ktor:ktor-client-cio:2.3.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    api("io.ktor:ktor-server-content-negotiation:2.3.0")
    api("io.ktor:ktor-client-content-negotiation:2.3.0")
    api("io.ktor:ktor-serialization-kotlinx-json:2.3.0")
    api(projects.gradleMetadataSchema)
    api("ch.qos.logback:logback-classic:1.4.6")
}