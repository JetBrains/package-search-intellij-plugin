@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create

class PackageSearchPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        apply<ShadowPlugin>()
        val packageSearchExtension =
            extensions.create<PackageSearchExtension>("packagesearch", project)
        val packageSearchPublicationExtension =
            packageSearchExtension.extensions
                .create<PackageSearchExtension.Publication>("publication", project)
        val packageSearchJavaExtension =
            packageSearchExtension.extensions
                .create<PackageSearchExtension.JavaToolchain>("java", project.objects)
        configureJavaPlugin(packageSearchJavaExtension)
        configureKotlinJvmPlugin(packageSearchExtension)
        configureGradleIntellijPlugin(packageSearchExtension)
        configurePublishPlugin(packageSearchPublicationExtension)
        configureLinting(packageSearchExtension)
    }


}

