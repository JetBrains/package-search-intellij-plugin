plugins {
    kotlin("jvm") apply false
}

allprojects {
    group = "org.jetbrains.packagesearch"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases")
    }
}