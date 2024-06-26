@file:Suppress("UnstableApiUsage")

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import java.lang.System.getenv
import kotlin.math.max
import org.jetbrains.intellij.tasks.PublishPluginTask
import org.jetbrains.kotlin.util.prefixIfNot
import org.jetbrains.kotlin.util.suffixIfNot
import org.jetbrains.packagesearch.gradle.lafFile
import org.jetbrains.packagesearch.gradle.logCategoriesFile
import org.jetbrains.packagesearch.gradle.patchLafFile
import org.jetbrains.packagesearch.gradle.patchLogFile
import org.jetbrains.packagesearch.gradle.patchSettingsFile
import org.jetbrains.packagesearch.gradle.patchTextRegistryFile
import org.jetbrains.packagesearch.gradle.registryTextFile
import org.jetbrains.packagesearch.gradle.settingsFile


repositories {
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        name = "Ktor EAP"
    }
}
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

intellij {
    plugins.add("org.jetbrains.idea.reposearch")
    plugins.add("com.jetbrains.performancePlugin")
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
    implementation(packageSearchCatalog.jewel.bridge.ij241)
    implementation(packageSearchCatalog.kotlinx.serialization.core)
    implementation(packageSearchCatalog.compose.desktop.components.splitpane) {
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.foundation")
    }
    implementation(packageSearchCatalog.ktor.client.logging)
    implementation(packageSearchCatalog.ktor.client.java)
    implementation(packageSearchCatalog.packagesearch.api.models)
    implementation(projects.plugin.gradle.base)
    implementation(projects.plugin.gradle.kmp)
    implementation(projects.plugin.maven)
    implementation(projects.plugin.utils)

    sourceElements(projects.plugin.core)
    sourceElements(projects.plugin.gradle)
    sourceElements(projects.plugin.gradle.base)
    sourceElements(projects.plugin.gradle.kmp)
    sourceElements(projects.plugin.maven)
    sourceElements(projects.plugin.utils)

    tooling(projects.plugin.gradle.tooling)

    testImplementation(kotlin("test-junit5"))
    testImplementation(packageSearchCatalog.ktor.client.mock)
    testImplementation(packageSearchCatalog.junit.jupiter.api)
    testImplementation(packageSearchCatalog.junit.jupiter.params)
    testImplementation(packageSearchCatalog.ide.starter.junit5)
    testImplementation(packageSearchCatalog.ide.starter.squashed)
    testImplementation(packageSearchCatalog.kotlinx.coroutines.test)
    testImplementation(packageSearchCatalog.assertk)
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

    val runNumber = getenv("RUN_NUMBER")?.toInt() ?: 0
    val runAttempt = getenv("RUN_ATTEMPT")?.toInt() ?: 0
    val snapshotMinorVersion = max(0, runNumber + runAttempt - 1)
    val versionString = project.version.toString()

    patchPluginXml {
        pluginId = pkgsPluginId
        version = versionString.replace("-SNAPSHOT", ".$snapshotMinorVersion")
        changeNotes = getenv("CHANGE_NOTES")
            ?.let { Parser.builder().build().parse(it) }
            ?.let { HtmlRenderer.builder().build().render(it) }
            ?.prefixIfNot("<![CDATA[")
            ?.suffixIfNot("]]>")

    }

    val buildShadowPlugin by registering(Zip::class) {
        group = "intellij"
        from(shadowJar) {
            rename { "packageSearch.jar" }
        }
        from(tooling) {
            rename { "gradle-tooling.jar" }
        }
        into("$pkgsPluginId/lib")
        archiveFileName = "packageSearch-${project.version}.zip"
            .replace("-SNAPSHOT", ".$snapshotMinorVersion")
        destinationDirectory = layout.buildDirectory.dir("distributions")
    }

    val testDataDirectoryPath = layout.buildDirectory
        .dir("testData")
        .map { it.asFile.absolutePath }

    test {
        dependsOn(buildShadowPlugin)
        environment("PKGS_PLUGIN_ID", pkgsPluginId)
        environment("PKGS_TEST_DATA_OUTPUT_DIR", testDataDirectoryPath.get())
        environment("KMP", true)
        val pluginArtifactDirectoryPath = buildShadowPlugin.get()
            .archiveFile.get()
            .asFile.absolutePath
        environment("PKGS_PLUGIN_ARTIFACT_FILE", pluginArtifactDirectoryPath)
        val cacheDir = layout.buildDirectory.dir("tests/cache")
        environment("CACHES", cacheDir.map { it.asFile.absolutePath }.get())
        doFirst {
            val cacheDirectory = cacheDir.get().asFile
            cacheDirectory.deleteRecursively()
            cacheDirectory.mkdirs()
        }
    }

    runIde {
        environment("PKGS_TEST_DATA_OUTPUT_DIR", testDataDirectoryPath.get())
    }

    register<PublishPluginTask>("publishSnapshotPluginToTBE") {
        group = "publishing"
        distributionFile = buildShadowPlugin.flatMap { it.archiveFile }
        toolboxEnterprise = true
        host = "https://tbe.labs.jb.gg/"
        token = project.properties["toolboxEnterpriseToken"]?.toString()
            ?: getenv("TOOLBOX_ENTERPRISE_TOKEN")
        channels = listOf("Snapshot")
    }

    register<PublishPluginTask>("publishReleasePluginToTBE") {
        group = "publishing"
        distributionFile = buildShadowPlugin.flatMap { it.archiveFile }
        toolboxEnterprise = true
        host = "https://tbe.labs.jb.gg/"
        token = project.properties["toolboxEnterpriseToken"]?.toString()
            ?: getenv("TOOLBOX_ENTERPRISE_TOKEN")
        channels = listOf("Release")
    }

    register<PublishPluginTask>("publishPluginToMarketplace") {
        group = "publishing"
        distributionFile = buildShadowPlugin.flatMap { it.archiveFile }
        token = project.properties["marketplaceToken"]?.toString()
            ?: getenv("MARKETPLACE_TOKEN")
    }

}
