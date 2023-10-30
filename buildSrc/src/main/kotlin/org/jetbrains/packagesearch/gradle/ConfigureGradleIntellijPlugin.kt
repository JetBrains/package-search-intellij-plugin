@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.gradle

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.tasks.PrepareSandboxTask

fun Project.configureGradleIntellijPlugin(packageSearchExtension: PackageSearchExtension) {
    val isCI = System.getenv("CI") != null

    plugins.withId("org.jetbrains.intellij") {
        extensions.withType<IntelliJPluginExtension> {
            version = packageSearchExtension.intellijVersion
            instrumentCode = false
            downloadSources = !isCI
        }
        tasks {
            val shadowJar = named<ShadowJar>("shadowJar") {
                relocate("io.ktor", "shadow.io.ktor")
                relocate("kotlinx.serialization", "shadow.kotlinx.serialization")
                relocate("kotlinx.datetime", "shadow.kotlinx.datetime")
                relocate("androidx", "shadow.androidx")
                relocate("org.jetbrains.jewel", "shadow.org.jetbrains.jewel")
                relocate("org.jetbrains.compose", "shadow.org.jetbrains.compose")
                exclude {
                    it.name.containsAny(packageSearchExtension.librariesToDelete.get())
                            && !it.name.containsAny(packageSearchExtension.librariesToKeep.get())
                }
                exclude { it.name == "module-info.class" }
            }
            named<PrepareSandboxTask>("prepareSandbox") {
                pluginJar = shadowJar.flatMap { it.archiveFile }
                runtimeClasspathFiles = objects.fileCollection()
            }
            named("runIde") {
                onlyIf { packageSearchExtension.isRunIdeEnabled.get() }
            }
        }
    }
}