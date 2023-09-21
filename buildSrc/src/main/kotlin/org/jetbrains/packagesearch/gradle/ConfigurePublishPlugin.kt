@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.gradle

import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registering
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.util.suffixIfNot

fun Project.configurePublishPlugin(publicationExtension: PackageSearchExtension.Publication) {
    plugins.withId("org.gradle.maven-publish") {
        plugins.withId("org.jetbrains.dokka") {
            plugins.withId("org.jetbrains.kotlin.jvm") {
                val dokkaHtml = tasks.named<DokkaTask>("dokkaHtml")
                val sourcesJar by tasks.registering(Jar::class) {
                    from(project.the<KotlinJvmProjectExtension>().sourceSets["main"].kotlin)
                    archiveClassifier = "sources"
                    destinationDirectory = layout.buildDirectory.dir("artifacts")
                }
                val javadocJar by tasks.registering(Jar::class) {
                    from(dokkaHtml)
                    archiveClassifier = "javadoc"
                    destinationDirectory = layout.buildDirectory.dir("artifacts")
                }
                extensions.withType<PublishingExtension> {
                    repositories {
                        pkgsSpace(project)
                    }
                    publications {
                        val publicationName = project.name
                            .replace(Regex(""""-([\w\W])""")) { it.groupValues[1].uppercase(Locale.getDefault()) }

                        register<MavenPublication>(publicationName) {
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