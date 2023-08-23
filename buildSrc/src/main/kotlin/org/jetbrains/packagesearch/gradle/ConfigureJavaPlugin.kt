@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.assign

fun Project.configureJavaPlugin(extension: PackageSearchExtension.JavaToolchain) {
    plugins.withId("org.gradle.java") {
        extensions.withType<JavaPluginExtension> {
            toolchain {
                languageVersion = extension.languageVersion
                vendor = extension.vendor
            }
        }

    }
}