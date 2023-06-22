plugins {
    java
    id(packageSearchCatalog.plugins.idea.gradle.plugin)
    `build-config`
    `maven-publish`
}

packagesearch {
    publication {
        isEnabled.set(true)
        artifactId.set("packagesearch-plugin-gradle")
    }
    java {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    from(sourceSets["main"].allSource)
    archiveClassifier.set("sources")
    archiveBaseName.set("sources")
    into("$buildDir/artifacts")
}

val javadocJar by tasks.registering(Jar::class) {
    from(tasks.javadoc.get().destinationDir)
    archiveClassifier.set("javadoc")
    archiveBaseName.set("javadoc")
    into("$buildDir/artifacts")
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
