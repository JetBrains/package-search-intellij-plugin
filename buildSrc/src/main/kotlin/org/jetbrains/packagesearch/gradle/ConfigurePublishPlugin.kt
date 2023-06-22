@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName
import org.jetbrains.kotlin.util.suffixIfNot

fun Project.configurePublishPlugin(publicationExtension: PackageSearchExtension.Publication) {
    plugins.withId("org.gradle.maven-publish") {
        plugins.withId("org.jetbrains.dokka") {
            plugins.withId("org.jetbrains.kotlin.jvm") {
                val dokkaHtml = tasks.named<DokkaTask>("dokkaHtml")
                val sourcesJar by tasks.registering(Jar::class) {
                    from(project.the<KotlinJvmProjectExtension>().sourceSets["main"].kotlin)
                    archiveClassifier.set("sources")
                    archivesName.set("sourcesJar")
                    into("$buildDir/artifacts")
                }
                val javadocJar by tasks.registering(Jar::class) {
                    from(dokkaHtml)
                    archiveClassifier.set("javadoc")
                    archivesName.set("javadocJar")
                    into("$buildDir/artifacts")
                }
                extensions.withType<PublishingExtension> {
                    repositories {
                        pkgsSpace(project)
                    }
                    publications {
                        register<MavenPublication>(project.name) {
                            artifact(javadocJar)
                            artifact(sourcesJar)
                            from(components["kotlin"])
                            afterEvaluate {
                                groupId = publicationExtension.groupId.get()
                                artifactId = publicationExtension.artifactId.get()
                                version = evaluateSpaceVersion(publicationExtension)
                                pom {
                                    publicationExtension.pomAction.get().invoke(this)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    tasks.withType<PublishToMavenRepository> {
        onlyIf { publicationExtension.isEnabled.get() }
    }
}


fun evaluateSpaceVersion(publicationExtension: PackageSearchExtension.Publication): String {
    val IS_SNAPSHOT: String? = System.getenv("IS_SNAPSHOT")
    val PUBLICATION_VERSION: String? = System.getenv("PUBLICATION_VERSION")
    return when {
        IS_SNAPSHOT == "true" -> publicationExtension.version.get().suffixIfNot("-SNAPSHOT")
        PUBLICATION_VERSION != null -> PUBLICATION_VERSION
        else -> publicationExtension.version.get()
    }
}

fun RepositoryHandler.pkgsSpace(project: Project) {
    maven {
        name = "Space"
        setUrl("https://packages.jetbrains.team/maven/p/kpm/public")
        credentials {
            username = System.getenv("MAVEN_SPACE_USERNAME") ?: project.extra.getStringOrNull("space.username")
            password = System.getenv("MAVEN_SPACE_PASSWORD") ?: project.extra.getStringOrNull("space.password")
        }
    }
}

fun ExtraPropertiesExtension.getStringOrNull(key: String) =
        runCatching { get(key)?.toString() }.getOrNull()