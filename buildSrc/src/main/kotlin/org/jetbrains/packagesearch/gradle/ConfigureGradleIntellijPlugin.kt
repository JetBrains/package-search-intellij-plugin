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
    plugins.withId("org.jetbrains.intellij") {
        extensions.withType<IntelliJPluginExtension> {
            version = packageSearchExtension.intellijVersion
        }
        tasks {
            val shadowJar = named<ShadowJar>("shadowJar") {
                relocate("io.ktor", "shadow.io.ktor")
                relocate("kotlinx.serialization", "shadow.kotlinx.serialization")
                exclude {
                    it.name.containsAny(packageSearchExtension.librariesToDelete.get())
                            && !it.name.containsAny(packageSearchExtension.librariesToKeep.get())
                }
            }
            named<PrepareSandboxTask>("prepareSandbox") {
                from(shadowJar)
                exclude { it.name != shadowJar.get().archiveFile.get().asFile.name }
            }
            named("runIde") {
                onlyIf { packageSearchExtension.isRunIdeEnabled.get() }
            }
        }
    }
}