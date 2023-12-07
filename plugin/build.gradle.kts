@file:Suppress("UnstableApiUsage")

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.intellij.tasks.PublishPluginTask
import org.jetbrains.packagesearch.gradle.lafFile
import org.jetbrains.packagesearch.gradle.logCategoriesFile
import org.jetbrains.packagesearch.gradle.patchLafFile
import org.jetbrains.packagesearch.gradle.patchLogFile
import org.jetbrains.packagesearch.gradle.patchSettingsFile
import org.jetbrains.packagesearch.gradle.patchTextRegistryFile
import org.jetbrains.packagesearch.gradle.registryTextFile
import org.jetbrains.packagesearch.gradle.settingsFile


plugins {
    id(packageSearchCatalog.plugins.kotlin.jvm)
    id(packageSearchCatalog.plugins.idea.gradle.plugin)
    id(packageSearchCatalog.plugins.dokka)
    alias(packageSearchCatalog.plugins.compose.desktop)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `build-config`
    `maven-publish`
}

packagesearch {
    publication {
        isEnabled = true
        artifactId = "packagesearch-plugin"
    }
    optIns.addAll(
        "androidx.compose.ui.ExperimentalComposeUiApi",
        "org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi",
        "org.jetbrains.jewel.ExperimentalJewelApi",
        "androidx.compose.foundation.ExperimentalFoundationApi",
        "kotlin.io.encoding.ExperimentalEncodingApi",
        "kotlin.ExperimentalStdlibApi",
        "kotlin.io.path.ExperimentalPathApi",
        "org.jetbrains.jewel.foundation.ExperimentalJewelApi"
    )
    isRunIdeEnabled = true
}

val tooling: Configuration by configurations.creating {
    isCanBeResolved = true
}

dependencies {
    implementation(compose.desktop.linux_arm64)
    implementation(compose.desktop.linux_x64)
    implementation(compose.desktop.macos_arm64)
    implementation(compose.desktop.macos_x64)
    implementation(compose.desktop.windows_x64)
    implementation(packageSearchCatalog.kotlinx.serialization.core)
    implementation(packageSearchCatalog.jewel.bridge.ij232)
    implementation(packageSearchCatalog.ktor.client.logging)
    implementation(packageSearchCatalog.packagesearch.api.models)
    implementation(projects.plugin.gradle.base)
    implementation(projects.plugin.gradle.kmp)
    implementation(projects.plugin.maven)

    sourceElements(projects.plugin.core)
    sourceElements(projects.plugin.gradle)
    sourceElements(projects.plugin.gradle.base)
    sourceElements(projects.plugin.gradle.kmp)
    sourceElements(projects.plugin.maven)

    tooling(projects.plugin.gradle.tooling)

    testImplementation(kotlin("test-junit5"))
    testImplementation(packageSearchCatalog.junit.jupiter.api)
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.engine)
}

val pkgsPluginId: String by project

tasks {
    val patchIdeSettings by registering {
        dependsOn(prepareSandbox)
        doLast {
            prepareSandbox.registryTextFile.get()
                .patchTextRegistryFile()
            prepareSandbox.lafFile.get()
                .patchLafFile()
            prepareSandbox.settingsFile.get()
                .patchSettingsFile()
            prepareSandbox.logCategoriesFile.get()
                .patchLogFile(pkgsPluginId)
        }
    }
    runIde {
        dependsOn(patchIdeSettings)
    }
    prepareSandbox {
        runtimeClasspathFiles = tooling
    }
    val snapshotDateSuffix = buildString {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        append(now.year)
        append(now.monthNumber)
        append(now.dayOfMonth)
        append(now.hour.toString().padStart(2, '0'))
        append(now.minute.toString().padStart(2, '0'))
        append(now.second.toString().padStart(2, '0'))
    }
    patchPluginXml {
        pluginId = pkgsPluginId
        val versionString = project.version.toString()
        version = when {
            versionString.endsWith("-SNAPSHOT") ->
                "${versionString.removePrefix("-SNAPSHOT")}.$snapshotDateSuffix"

            else -> versionString
        }
    }
    val buildShadowPlugin by registering(Zip::class) {
        group = "intellij"
        from(shadowJar) {
            rename {
                "package-search-plugin" + when {
                    it.endsWith("-SNAPSHOT.jar") ->
                        it.replace("-SNAPSHOT.jar", ".$snapshotDateSuffix.jar")
                            .also { logger.lifecycle("Snapshot version -> $it") }

                    else -> it
                }
            }
        }
        from(tooling) {
            rename { "gradle-tooling.jar" }
        }
        into("$pkgsPluginId/lib")
        archiveFileName.set("packagesearch-plugin.zip")
        destinationDirectory = layout.buildDirectory.dir("distributions")
    }

    register<PublishPluginTask>("publishShadowPlugin") {
        group = "publishing"
        distributionFile = buildShadowPlugin.flatMap { it.archiveFile }
        toolboxEnterprise = true
        host = "https://tbe.labs.jb.gg/"
        token = project.properties["toolboxEnterpriseToken"]?.toString()
            ?: System.getenv("TOOLBOX_ENTERPRISE_TOKEN")
        channels = listOf("Stable")
    }

    register<PublishPluginTask>("publishShadowPluginToMarketplace") {
        group = "publishing"
        distributionFile = buildShadowPlugin.flatMap { it.archiveFile }
        token = project.properties["marketplaceToken"]?.toString()
            ?: System.getenv("MARKETPLACE_TOKEN")
    }

}
