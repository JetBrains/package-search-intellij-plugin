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


plugins {
    id(packageSearchCatalog.plugins.kotlin.jvm)
    id(packageSearchCatalog.plugins.dokka)
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
    api(packageSearchCatalog.kotlinx.serialization.core)
    api(packageSearchCatalog.packagesearch.api.client)
    api(packageSearchCatalog.potassium.nitrite)
    api(packageSearchCatalog.nitrite)
    api(packageSearchCatalog.nitrite.mvstore.adapter)

    testImplementation(kotlin("test-junit5"))
    testImplementation(packageSearchCatalog.ktor.client.mock)
    testImplementation(packageSearchCatalog.junit.jupiter.api)
    testImplementation(packageSearchCatalog.junit.jupiter.params)
    testImplementation(packageSearchCatalog.kotlinx.coroutines.test)
    testImplementation(packageSearchCatalog.assertk)
    testRuntimeOnly(packageSearchCatalog.junit.jupiter.engine)
    testImplementation(packageSearchCatalog.logback.classic)
}

tasks {
    test {
        val cacheDir = layout.buildDirectory.dir("tests/cache")
        environment("CACHES", cacheDir.map { it.asFile.absolutePath }.get())
        doFirst {
            val cacheDirectory = cacheDir.get().asFile
            cacheDirectory.deleteRecursively()
            cacheDirectory.mkdirs()
        }
    }
}
