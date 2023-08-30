package org.jetbrains.packagesearch.gradle

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.registering
import org.jmailen.gradle.kotlinter.KotlinterExtension
import java.net.URL

val currentJarPath: URL
    get() = PackageSearchExtension::class.java
        .protectionDomain
        .codeSource
        .location

fun Project.configureLinting(extension: PackageSearchExtension) {
    plugins.withId("io.gitlab.arturbosch.detekt") {
        val generateDetektConfigFile by tasks.registering(Sync::class) {
            group = "detekt"
            from(zipTree(currentJarPath)) {
                include { it.name == "detekt.yml" }
            }
            into(layout.buildDirectory.dir("detekt"))
        }
        extensions.withType<DetektExtension> {
            toolVersion = "1.20.0"
            autoCorrect = !isCi
            afterEvaluate {
                if (extension.detektFile.isPresent)
                    config.from(extension.detektFile.asFile.get().absolutePath)
                else config.builtBy(generateDetektConfigFile)
            }
            buildUponDefaultConfig = true
        }
    }
    plugins.withId("") {
        extensions.withType<KotlinterExtension> {
            reporters = arrayOf("html", "checkstyle", "plain")
        }
    }
}

val isCi
    get() = System.getenv("CI") != null || System.getenv("CONTINUOUS_INTEGRATION") != null

