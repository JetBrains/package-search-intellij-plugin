import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
}

intellij {
    version.set("LATEST-EAP-SNAPSHOT")
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
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${getKotlinPluginVersion()}")
    implementation("org.gradle:gradle-tooling-api:8.1.1") {
        exclude(group = "org.jetbrains.kotlinx")
    }
    implementation("org.jetbrains.jewel:foundation") {
        exclude(group = "org.jetbrains.kotlinx")
    }
    api("org.jetbrains.packagesearch:package-search-api-models") {
        exclude(group = "org.jetbrains.kotlinx")
    }
    api("org.jetbrains.packagesearch:package-search-version-utils") {
        exclude(group = "org.jetbrains.kotlinx")
    }
    implementation("io.ktor:ktor-client-cio:2.3.0") {
        exclude(group = "org.jetbrains.kotlinx")
    }
    implementation(projects.plugin.maven) {
        exclude(group = "org.jetbrains.kotlinx")
    }
    implementation(projects.plugin.core) {
        exclude(group = "org.jetbrains.kotlinx")
    }
}

val toDelete = listOf(
    "kotlinx-coroutines",
    "kotlin-stdlib",
    "kotlinx-serialization",
    "slf4j-api"
)

tasks {
    val removePlatformLibs by registering(Sync::class) {
        from(prepareSandbox) {
            include { !it.name.containsAny(toDelete) }
        }
        into(
            prepareSandbox
                .flatMap { it.defaultDestinationDir }
                .map {
                    it.toPath()
                        .parent
                        .resolve("filtered-plugins")
                }
        )
    }
    runIde {
        dependsOn(removePlatformLibs)
        pluginsDir.set(removePlatformLibs.get().destinationDir)
    }
}

fun String.containsAny(toDelete: List<String>) =
    toDelete.any { it in this }
