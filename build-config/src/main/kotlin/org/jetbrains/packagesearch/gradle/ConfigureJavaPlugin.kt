package org.jetbrains.packagesearch.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

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