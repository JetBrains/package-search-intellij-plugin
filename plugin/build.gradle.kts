@file:Suppress("UnstableApiUsage")

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import java.lang.System.getenv
import kotlin.math.max
import org.jetbrains.intellij.platform.gradle.tasks.GenerateManifestTask
import org.jetbrains.intellij.platform.gradle.tasks.PublishPluginTask
import org.jetbrains.kotlin.util.prefixIfNot


plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    id(packageSearchCatalog.plugins.idea.gradle.plugin.platform)
    alias(packageSearchCatalog.plugins.dokka)
    alias(packageSearchCatalog.plugins.compose.desktop)
    alias(packageSearchCatalog.plugins.kotlin.plugin.compose)
    id(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    alias(packageSearchCatalog.plugins.shadow)
    `maven-publish`
}
intellijPlatform {
    instrumentCode = false
}

val tooling: Configuration by configurations.creating {
    isCanBeResolved = true
}

dependencies {

    intellijPlatform {
        intellijIdeaCommunity(INTELLIJ_VERSION)
        bundledPlugins(
            "org.jetbrains.idea.reposearch",
            "com.jetbrains.performancePlugin",
        )
        bundledModule(
            "intellij.platform.compose"
        )
    }

    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.kotlinx")
    }
    implementation(packageSearchCatalog.jewel.bridge.ij243) //compileonly???
    implementation(packageSearchCatalog.kotlinx.serialization.core)
    implementation(packageSearchCatalog.compose.desktop.components.splitpane) {
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.foundation")
    }
    implementation(packageSearchCatalog.ktor.client.logging)
    implementation(packageSearchCatalog.ktor.client.java)
    implementation(projects.plugin.gradle.base)
    implementation(projects.plugin.gradle.kmp)
    implementation(projects.plugin.maven)
    implementation(projects.plugin.utils)

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

tasks {
    prepareSandbox {
        runtimeClasspath = tooling
        pluginJar = shadowJar.flatMap { it.archiveFile }
    }

    val runNumber = getenv("RUN_NUMBER")?.toInt() ?: 0
    val runAttempt = getenv("RUN_ATTEMPT")?.toInt() ?: 0
    val snapshotMinorVersion = max(0, runNumber + runAttempt - 1)
    val versionString = project.version.toString()

    patchPluginXml {
        pluginId = PACKAGE_SEARCH_PLUGIN_ID
        version = versionString.replace("-SNAPSHOT", ".$snapshotMinorVersion")
        changeNotes = getenv("CHANGE_NOTES")
            ?.let { Parser.builder().build().parse(it) }
            ?.let { HtmlRenderer.builder().build().render(it) }
//            ?.prefixIfNot("<![CDATA[")
//            ?.suffixIfNot("]]>")

    }

    shadowJar {
        val generateManifestTask = named<GenerateManifestTask>("generateManifest")
        dependsOn(generateManifestTask)
        manifest.from(generateManifestTask.flatMap<RegularFile> { it.generatedManifest })
        exclude { it.name.containsAny(JAR_NAMES_TO_REMOVE) }
        exclude { it.name == "module-info.class" }
        exclude { it.name.endsWith("kotlin_module") }
    }

    val buildShadowPlugin by registering(Zip::class) {
        group = "intellij"
        from(shadowJar) {
            rename { "packageSearch.jar" }
        }
        from(tooling) {
            rename { "gradle-tooling.jar" }
        }
        into("$PACKAGE_SEARCH_PLUGIN_ID/lib")
        archiveFileName = "packageSearch-${project.version}.zip"
            .replace("-SNAPSHOT", ".$snapshotMinorVersion")
        destinationDirectory = layout.buildDirectory.dir("distributions")
    }

    val testDataDirectoryPath = layout.buildDirectory
        .dir("testData")
        .map { it.asFile.absolutePath }

    test {
        dependsOn(buildShadowPlugin)
        environment("PKGS_PLUGIN_ID", PACKAGE_SEARCH_PLUGIN_ID)
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
        ideServices = true
        group = "publishing"
        archiveFile = buildShadowPlugin.flatMap { it.archiveFile }
        host = "https://tbe.labs.jb.gg/"
        token = project.properties["toolboxEnterpriseToken"]?.toString()
            ?: getenv("TOOLBOX_ENTERPRISE_TOKEN")
        channels = listOf("Snapshot")
    }

    register<PublishPluginTask>("publishReleasePluginToTBE") {
        ideServices = true
        group = "publishing"
        archiveFile = buildShadowPlugin.flatMap { it.archiveFile }
        host = "https://tbe.labs.jb.gg/"
        token = project.properties["toolboxEnterpriseToken"]?.toString()
            ?: getenv("TOOLBOX_ENTERPRISE_TOKEN")
        channels = listOf("Release")
    }

    register<PublishPluginTask>("publishPluginToMarketplace") {
        group = "publishing"
        archiveFile = buildShadowPlugin.flatMap { it.archiveFile }
        token = project.properties["marketplaceToken"]?.toString()
            ?: getenv("MARKETPLACE_TOKEN")
    }

}

