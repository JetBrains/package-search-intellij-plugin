@file:Suppress("UnstableApiUsage")

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.intellij.jewel.workshop.build.lafFile
import org.intellij.jewel.workshop.build.logCategoriesFile
import org.intellij.jewel.workshop.build.patchLafFile
import org.intellij.jewel.workshop.build.patchLogFile
import org.intellij.jewel.workshop.build.patchSettingsFile
import org.intellij.jewel.workshop.build.patchTextRegistryFile
import org.intellij.jewel.workshop.build.registryTextFile
import org.intellij.jewel.workshop.build.settingsFile
import org.jetbrains.intellij.tasks.PublishPluginTask


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
        "kotlin.ExperimentalStdlibApi"
    )
    isRunIdeEnabled = true
}

dependencies {
    implementation(projects.apiMock.apiMockClient)
    implementation(compose.desktop.linux_arm64)
    implementation(compose.desktop.linux_x64)
    implementation(compose.desktop.macos_arm64)
    implementation(compose.desktop.macos_x64)
    implementation(compose.desktop.windows_x64)
    implementation(packageSearchCatalog.kotlinx.serialization.core)
    implementation(packageSearchCatalog.compose.desktop.components.splitpane)
    implementation(packageSearchCatalog.jewel.bridge)
    implementation(packageSearchCatalog.jewel.ui)
    implementation(packageSearchCatalog.jewel.foundation)
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

    testImplementation(kotlin("test-junit5"))
    testImplementation(packageSearchCatalog.junit.jupiter.api)
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.engine)
}

val tooling: Configuration by configurations.creating {
    isCanBeResolved = true
}

dependencies {
    tooling(projects.plugin.gradle.tooling)
}

val pkgsPluginId = "com.jetbrains.packagesearch.intellij-plugin"

val generatedDir: Provider<Directory> = layout.buildDirectory.dir("generated/main/kotlin")
kotlin.sourceSets.main {
    kotlin.srcDirs(generatedDir)
}

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
    val generatePluginDataSources by registering {
        outputs.dir(generatedDir)
        doLast {
            val fileSpec = FileSpec.builder("com.jetbrains.packagesearch.plugin", "PackageSearch")
                .addType(
                    TypeSpec.objectBuilder("PackageSearch")
                        .addModifiers(KModifier.DATA)
                        .addProperty(
                            PropertySpec.builder("pluginId", String::class)
                                .getter(
                                    FunSpec.getterBuilder()
                                        .addStatement("return %S", pkgsPluginId)
                                        .build()
                                )
                                .build()
                        )
                        .addProperty(
                            PropertySpec.builder("pluginVersion", String::class)
                                .getter(
                                    FunSpec.getterBuilder()
                                        .addStatement("return %S", version.toString())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
            fileSpec.writeTo(generatedDir.get().asFile)
        }
    }
    sourcesJar {
        dependsOn(generatePluginDataSources)
    }
    javadocJar {
        dependsOn(generatePluginDataSources)
    }
    shadowJar {
        archiveBaseName = "packagesearch-plugin"
    }
    prepareSandbox {
        runtimeClasspathFiles = tooling
    }
    compileKotlin {
        dependsOn(generatePluginDataSources)
    }
    patchPluginXml {
        pluginId = pkgsPluginId
    }

    val buildShadowPlugin by registering(Zip::class) {
        group = "intellij"
        from(shadowJar, tooling, jarSearchableOptions)
        into("com.jetbrains.packagesearch.intellij-plugin/lib") // <-- ONLY ONE into()!
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
        channels = listOf("INTERNAL-EAP")
    }

}
