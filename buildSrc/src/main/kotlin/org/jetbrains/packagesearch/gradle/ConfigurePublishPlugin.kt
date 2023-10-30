@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.gradle

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.component.DefaultAdhocSoftwareComponent
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.getting
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registering
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.util.suffixIfNot

fun Project.configurePublishPlugin(
    publicationExtension: PackageSearchExtension.Publication,
    softwareComponentFactory: SoftwareComponentFactory,
) {
    plugins.withId("org.gradle.maven-publish") {
        plugins.withId("org.gradle.java-base") {
            val sourcesJar by tasks.registering(Jar::class) {
                from(project.the<SourceSetContainer>().named("main").map { it.allSource })
                plugins.withId("org.jetbrains.kotlin.jvm") {
                    from(project.the<KotlinJvmProjectExtension>().sourceSets["main"].kotlin)
                }
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
                archiveClassifier = "sources"
                destinationDirectory = layout.buildDirectory.dir("artifacts")
            }
            tasks.named<ShadowJar>("shadowJar") {
                archiveClassifier = null
                destinationDirectory = layout.buildDirectory.dir("artifacts/shadow")
            }
            val sourcesConfiguration = configurations.register("sourceElements") {
                isCanBeConsumed = true
                isCanBeResolved = true
                isCanBeDeclared = true
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
                }
                outgoing {
                    artifact(sourcesJar)
                }
            }
            val shadowSourcesJar by tasks.registering(Jar::class) {
                dependsOn(sourcesConfiguration, sourcesJar)
                from(sourcesConfiguration.map { it.resolvedConfiguration.files.map { zipTree(it) } })
                from(sourcesJar.flatMap { it.archiveFile }.map { zipTree(it) })
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
                archiveClassifier = "sources"
                destinationDirectory = layout.buildDirectory.dir("artifacts/shadow")
            }
            val shadowSourcesConfiguration = configurations.register("shadowSourcesElements") {
                isCanBeConsumed = false
                isCanBeResolved = false
                isCanBeDeclared = false
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
                }
                outgoing {
                    artifact(shadowSourcesJar)
                }
            }
            val java: DefaultAdhocSoftwareComponent by components
            val sourcesComponent = softwareComponentFactory.adhoc("sources")
            sourcesComponent.addVariantsFromConfiguration(sourcesConfiguration.get()) {
                mapToMavenScope("runtime")
            }
            val shadowComponent = softwareComponentFactory.adhoc("shadow")
            val shadowRuntimeElements by configurations.getting
            shadowComponent.addVariantsFromConfiguration(shadowRuntimeElements) {
                mapToMavenScope("runtime")
            }
            shadowComponent.addVariantsFromConfiguration(shadowSourcesConfiguration.get()) {
                mapToMavenScope("runtime")
            }
            java.addVariantsFromConfiguration(sourcesConfiguration.get()) {
                mapToMavenScope("runtime")
            }
            val rootComponent =
                object : ComponentWithVariants, SoftwareComponentInternal by java {
                    override fun getUsages() = java.usages
                        .filter { it.name != "shadowRuntimeElements" }
                        .toSet()

                    override fun getVariants(): Set<SoftwareComponent> =
                        if (publicationExtension.publishShadow.get()) setOf(shadowComponent) else emptySet()
                }
            extensions.withType<PublishingExtension> {
                repositories {
                    pkgsSpace(project)
                    maven {
                        name = "test"
                        setUrl(rootProject.layout.buildDirectory.dir("maven"))
                    }
                }
                publications {
                    val publicationName = project.name
                        .replace(Regex(""""-([\w\W])""")) { it.groupValues[1].uppercase(Locale.getDefault()) }
                    afterEvaluate {
                        if (publicationExtension.publishShadow.get()) {
                            register<MavenPublication>(publicationName + "Shadow") {
                                from(shadowComponent)
                                afterEvaluate {
                                    groupId = publicationExtension.groupId.get()
                                    artifactId = publicationExtension.artifactId.get() + "-shadow"
                                    version = evaluateSpaceVersion(publicationExtension)
                                    afterEvaluate {
                                        groupId = publicationExtension.groupId.get()
                                        artifactId = publicationExtension.artifactId.get() + "-shadow"
                                        version = evaluateSpaceVersion(publicationExtension)
                                        pom {
                                            publicationExtension.pomAction.get().invoke(this)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    register<MavenPublication>(publicationName) {
                        plugins.withId("org.jetbrains.dokka") {
                            val dokkaHtml = tasks.named<DokkaTask>("dokkaHtml")
                            val javadocJar by tasks.registering(Jar::class) {
                                from(dokkaHtml)
                                archiveClassifier = "javadoc"
                                destinationDirectory = layout.buildDirectory.dir("artifacts")
                            }
                            artifact(javadocJar)
                        }
                        from(rootComponent)
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

