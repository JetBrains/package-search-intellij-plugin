@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.gradle

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.maven.MavenPom
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

abstract class PackageSearchExtension(project: Project) : ExtensionAware {

    abstract class Publication(project: Project) : ExtensionAware {

        val isEnabled = project.objects.property<Boolean>()
            .convention(false)

        val publishShadow = project.objects.property<Boolean>()
            .convention(true)

        val artifactId = project.objects.property<String>()
            .convention(project.name)
        val groupId = project.objects.property<String>()
            .convention(project.group.toString())
        val version = project.objects.property<String>()
            .convention(project.provider { project.version.toString() })

        internal val pomAction = project.objects
            .property<MavenPom.() -> Unit>()
            .convention {  }

        fun pom(action: MavenPom.() -> Unit) {
            pomAction = action
        }
    }

    abstract class JavaToolchain(objectFactory: ObjectFactory) : ExtensionAware {
        val languageVersion = objectFactory.property<JavaLanguageVersion>()
            .convention(JavaLanguageVersion.of(17))
        val vendor = objectFactory.property<JvmVendorSpec>()
            .convention(JvmVendorSpec.AZUL)
    }

    val jvmTarget = project.objects.property<JvmTarget>()
        .convention(JvmTarget.JVM_17)

    val optIns = project.objects.listProperty<String>()
        .apply {
            set(
                listOf(
                    "kotlinx.serialization.ExperimentalSerializationApi",
                    "kotlinx.serialization.InternalSerializationApi",
                    "kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI",
                    "kotlinx.coroutines.FlowPreview",
                    "kotlin.contracts.ExperimentalContracts"
                )
            )
        }

    val librariesToDelete = project.objects.listProperty<String>()
        .apply {
            addAll(
                "kotlin-stdlib",
                "slf4j",
                "kotlin-reflect",
                "kotlinx-coroutines",
                "logback",
            )
        }

    val librariesToKeep = project.objects.listProperty<String>()

    val detektFile = project.objects.fileProperty()

    val isRunIdeEnabled = project.objects.property<Boolean>()
        .convention(false)

}
