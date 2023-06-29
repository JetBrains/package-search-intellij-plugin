package org.jetbrains.packagesearch.gradle

import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.tasks.PrepareSandboxTask


fun Project.configureGradleIntellijPlugin(packageSearchExtension: PackageSearchExtension) {
    plugins.withId("org.jetbrains.intellij") {
        extensions.withType<IntelliJPluginExtension> {
            version.set(packageSearchExtension.intellijVersion)
        }
        tasks {
            named<PrepareSandboxTask>("prepareSandbox") {
                doLast {
                    destinationDir.walkTopDown().forEach { file ->
                        if (file.name.containsAny(packageSearchExtension.librariesToDelete.get())) {
                            file.delete()
                        }
                    }
                }
            }
        }
    }
}