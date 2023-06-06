plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm) apply false
    alias(packageSearchCatalog.plugins.kotlin.multiplatform) apply false
    `version-catalog`
    `maven-publish`
}

allprojects {
    group = "org.jetbrains.packagesearch"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases")
    }
}

publishing {
    publications {
        create<MavenPublication>("packageSearchVersionCatalog") {
            from(components["versionCatalog"])
        }
    }
}