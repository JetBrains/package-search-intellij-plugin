val kotlinVersion = "1.7.20-Beta"

plugins {
    kotlin("multiplatform") version "1.7.20-Beta"
    application //to run JVM part
}

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        withJava()
    }

    sourceSets {
    }
}