@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName
import org.jetbrains.kotlin.util.suffixIfNot

fun Project.configurePublishPlugin(publicationExtension: PackageSearchExtension.Publication) = afterEvaluate {
    if (!publicationExtension.isEnabled.get()) return@afterEvaluate
    plugins.withId("org.gradle.maven-publish") {
        plugins.withId("org.jetbrains.dokka") {
            plugins.withId("org.jetbrains.kotlin.jvm") {
                val sourcesJar by tasks.registering(Jar::class) {
                    from(
                        extensions.getByType<KotlinJvmProjectExtension>()
                            .sourceSets["main"]
                            .kotlin
                    )
                    from(extensions.getByType<SourceSetContainer>()["main"].java)
                    archiveClassifier.set("sources")
                    archivesName.set("sourcesJar")
                    into("$buildDir/artifacts")
                }
                val dokkaHtml = tasks.named<DokkaTask>("dokkaHtml")
                val javadocJar by tasks.registering(Jar::class) {
                    from(dokkaHtml)
                    archiveClassifier.set("javadoc")
                    archivesName.set("javadocJar")
                    into("$buildDir/artifacts")
                }
                extensions.withType<PublishingExtension> {
                    publications {
                        register<MavenPublication>(project.name) mavenPub@{
                            from(components["kotlin"])
                            artifact(javadocJar)
                            artifact(sourcesJar)
                            groupId = publicationExtension.groupId.get()
                            artifactId = publicationExtension.artifactId.get()
                            this@mavenPub.version = evaluateGithubVersion(publicationExtension)
                            pom {
                                publicationExtension.pomAction.get().invoke(this)
                            }
                        }
                    }
                    repositories {
                        pkgsSpace()
                    }
                }
            }
        }
    }
}

fun Project.evaluateGithubVersion(publicationExtension: PackageSearchExtension.Publication): String? {
    val GITHUB_REF: String? = System.getenv("GITHUB_REF")
    return when {
        GITHUB_REF == null -> publicationExtension.version.get()
        GITHUB_REF.startsWith("refs/tags/") -> GITHUB_REF.removePrefix("refs/tags/")
        GITHUB_REF == "refs/heads/main" || GITHUB_REF == "refs/heads/dev" ->
            project.version.toString().suffixIfNot("-SNAPSHOT")

        else -> publicationExtension.version.get()
    }
}

fun RepositoryHandler.pkgsSpace() {
    maven {
        name = "Space"
        setUrl("https://packages.jetbrains.team/maven/p/kpm/public")
        credentials {
            username = System.getenv("MAVEN_SPACE_USERNAME")
            password = System.getenv("MAVEN_SPACE_PASSWORD")
        }
    }
}

