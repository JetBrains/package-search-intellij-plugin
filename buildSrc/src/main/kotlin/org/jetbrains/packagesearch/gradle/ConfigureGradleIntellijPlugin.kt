package org.jetbrains.packagesearch.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.RunIdeTask


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