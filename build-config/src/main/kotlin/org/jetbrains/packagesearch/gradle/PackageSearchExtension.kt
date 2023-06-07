package org.jetbrains.packagesearch.gradle

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.maven.MavenPom
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

abstract class PackageSearchExtension(project: Project) : ExtensionAware {

    abstract class Publication(project: Project) : ExtensionAware {
        val artifactId = project.objects.property<String>()
            .convention(project.name)
        val groupId = project.objects.property<String>()
            .convention(project.group.toString())
        val version = project.objects.property<String>()
            .convention(project.version.toString())

        internal val pomAction = project.objects.property<MavenPom.() -> Unit>()
            .convention {  }

        fun pom(action: MavenPom.() -> Unit) {
            pomAction.set(action)
        }
    }

    val jvmTarget = project.objects.property<JvmTarget>()
        .convention(JvmTarget.JVM_17)

    val javaVersion = project.objects.property<JavaLanguageVersion>()
        .convention(JavaLanguageVersion.of(17))

    val optIns = project.objects.listProperty<String>()
        .apply {
            set(
                listOf(
                    "kotlinx.serialization.ExperimentalSerializationApi",
                    "kotlinx.serialization.InternalSerializationApi",
                    "kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "org.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI"
                )
            )
        }

    val librariesToDelete = project.objects.listProperty<String>()
        .apply {
            addAll(
                "kotlin-stdlib",
                "ktor-",
                "slf4j",
                "kotlin-reflect",
                "kotlinx-"
            )
        }

    val intellijVersion = project.objects.property<String>()
        .convention("LATEST-EAP-SNAPSHOT")

}