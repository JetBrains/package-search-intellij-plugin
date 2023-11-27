@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.provideDelegate

class PackageSearchPlugin @Inject constructor(
    private val softwareComponentFactory: SoftwareComponentFactory,
) : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        apply<ShadowPlugin>()
        val packageSearchExtension =
            extensions.create<PackageSearchExtension>("packagesearch", project)

        val intelliJVersion: String? by project
        packageSearchExtension.intellijVersion
            .convention(
                intelliJVersion?.let {
                        SupportedIntelliJVersion
                            .values()
                            .firstOrNull { enumName -> enumName.name == it }
                    }
                    ?: SupportedIntelliJVersion.`233`
            )

        logger.lifecycle("PackageSearchPlugin: intelliJVersion = " +
                "${packageSearchExtension.intellijVersion.get()}")

        val packageSearchPublicationExtension =
            packageSearchExtension.extensions
                .create<PackageSearchExtension.Publication>("publication", project)
        val packageSearchJavaExtension =
            packageSearchExtension.extensions
                .create<PackageSearchExtension.JavaToolchain>("java", project.objects)
        configureJavaPlugin(packageSearchJavaExtension)
        configureKotlinJvmPlugin(packageSearchExtension)
        configureGradleIntellijPlugin(packageSearchExtension)
        configurePublishPlugin(packageSearchPublicationExtension, softwareComponentFactory)
        configureLinting(packageSearchExtension)
    }

}

