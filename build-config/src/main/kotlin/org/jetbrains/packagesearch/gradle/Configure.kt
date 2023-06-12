@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.*
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

fun Project.configureGradleIntellijPlugin(packageSearchExtension: PackageSearchExtension) {
    plugins.withId("org.jetbrains.intellij") {
        extensions.withType<IntelliJPluginExtension> {
            version.set(packageSearchExtension.intellijVersion)
        }
        tasks {
            val prepareSandbox = named<PrepareSandboxTask>("prepareSandbox")
            val removePlatformLibs by registering(Sync::class) {
                from(prepareSandbox) {
                    include { !it.name.containsAny(packageSearchExtension.librariesToDelete.get()) }
                }
                into(prepareSandbox.flatMap { it.defaultDestinationDir }.map {
                    it.toPath().parent.resolve("filtered-plugins")
                })
            }
            named<RunIdeTask>("runIde") {
                dependsOn(removePlatformLibs)
                pluginsDir.set(removePlatformLibs.get().destinationDir)
            }
        }
    }
}

fun Project.configureKotlinJvmPlugin(packageSearchExtension: PackageSearchExtension) {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        tasks {
            withType<KotlinJvmCompile> {
                compilerOptions {
                    jvmTarget.set(packageSearchExtension.jvmTarget)
                    freeCompilerArgs.addAll(packageSearchExtension.optIns.map { it.map { "-opt-in=$it" } })
                }
            }
            withType<Test> {
                useJUnitPlatform()
            }
        }
    }
}

fun Project.configureJavaPlugin(extension: PackageSearchExtension.JavaToolchain) {
    plugins.withId("org.gradle.java") {
        extensions.withType<JavaPluginExtension> {
            toolchain {
                languageVersion.set(extension.languageVersion)
                vendor.set(extension.vendor)
            }
        }

    }
}

fun Project.configurePublishPlugin(publicationExtension: PackageSearchExtension.Publication) {
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
                        create<MavenPublication>(project.name) mavenPub@{
                            from(components["kotlin"])
                            artifact(javadocJar)
                            artifact(sourcesJar)
                            afterEvaluate {
                                groupId = publicationExtension.groupId.get()
                                artifactId = publicationExtension.artifactId.get()
                                this@mavenPub.version = publicationExtension.version.get()
                                pom {
                                    url.set("https://package-search.jetbrains.com/")
                                    scm {
                                        connection.set("scm:https://git.jetbrains.team/kpm/pkgs-plugin-v2.git")
                                        developerConnection.set("scm:https://git.jetbrains.team/kpm/pkgs-plugin-v2.git")
                                        url.set("https://jetbrains.team/p/kpm/repositories/pkgs-plugin-v2/files")
                                    }
                                    publicationExtension.pomAction.get().invoke(this)
                                }
                            }
                        }
                    }
                    repositories {
                        maven {
                            name = "Space"
                            setUrl("https://packages.jetbrains.team/maven/p/kpm/public")
                            credentials {
                                username = System.getenv("MAVEN_SPACE_USERNAME")
                                password = System.getenv("MAVEN_SPACE_PASSWORD")
                            }
                        }
                    }
                }
            }
        }
    }
}

