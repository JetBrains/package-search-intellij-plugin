@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

fun Project.configureKotlinJvmPlugin(packageSearchExtension: PackageSearchExtension) {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        tasks {
            withType<KotlinJvmCompile> {
                compilerOptions {
                    jvmTarget = packageSearchExtension.jvmTarget
                    freeCompilerArgs.addAll(packageSearchExtension.optIns.map { it.map { "-opt-in=$it" } })
                    freeCompilerArgs.add("-Xcontext-receivers")
                }
            }
            withType<Test> {
                useJUnitPlatform()
            }
        }
    }
}