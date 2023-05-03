plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
}

intellij {
    version.set("2023.1")
    plugins.addAll(
        "org.jetbrains.idea.maven",
        "org.jetbrains.plugins.gradle"
    )
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    target {
        compilations.all {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xcontext-receivers")
            }
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
    implementation("org.gradle:gradle-tooling-api:8.1.1")
    implementation("org.jetbrains.jewel:foundation") {
        exclude(group = "org.jetbrains.kotlinx")
    }
    implementation("org.jetbrains.packagesearch:package-search-api-models")
    implementation("org.jetbrains.packagesearch:package-search-version-utils")
    implementation("io.ktor:ktor-client-cio:2.3.0")
    implementation("org.dizitart:nitrite:3.4.4") {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.squareup.okhttp3")
        exclude(group = "org.slf4j")
    }
}