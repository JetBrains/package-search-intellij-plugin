@file:Suppress("UnstableApiUsage")

plugins {
    java
    id(packageSearchCatalog.plugins.idea.gradle.plugin)
    `build-config`
    `maven-publish`
}

packagesearch {
    publication {
        isEnabled = true
        artifactId = "packagesearch-plugin-gradle"
    }
    java {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    from(sourceSets["main"].allSource)
    archiveClassifier = "sources"
    destinationDirectory = layout.buildDirectory.dir("artifacts")
}

val javadocJar by tasks.registering(Jar::class) {
    from(tasks.javadoc.get().destinationDir)
    archiveClassifier = "javadoc"
    destinationDirectory = layout.buildDirectory.dir("artifacts")
}

publishing {
    publications {
        register<MavenPublication>(project.name) {
            from(components["java"])
            artifact(javadocJar)
            artifact(sourcesJar)
            artifactId = "packagesearch-plugin-gradle-tooling"
        }
    }
}

intellij {
    plugins.add("org.jetbrains.plugins.gradle")
}
