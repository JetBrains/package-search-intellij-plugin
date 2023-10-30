package org.jetbrains.packagesearch.gradle

import java.io.File
import java.nio.file.Paths
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.intellij.tasks.PrepareSandboxTask

fun File.patchTextRegistryFile() {
    val registry = runCatching { readLines() }.getOrNull()
        ?: emptyList()

    val indexOfExperimentalUi = registry.indexOf("ide.experimental.ui")
    val newRegistry = if (indexOfExperimentalUi >= 0) {
        registry.mapIndexed { index, value ->
            if (index == indexOfExperimentalUi + 1) "true" else value
        }
    } else {
        registry + listOf("ide.experimental.ui", "true")
    }
    writeText(newRegistry.joinToString("\n"))
}

val xml = XML {
    indentString = "  "
    defaultPolicy {
        ignoreUnknownChildren()
    }
}

fun File.patchLafFile() {
    val lafSettings = runCatching {
        xml.decodeFromString<Application>(readText())
    }.getOrNull()

    val currentTheme = lafSettings?.components
        ?.firstOrNull()
        ?.laf
        ?.themeId

    if (currentTheme in Application.Laf.newUiStrings) return
    writeText(xml.encodeToString(Application.Laf.DARK))
}

fun File.patchSettingsFile() {
    val settings = runCatching {
        xml.decodeFromString<Application>(readText())
    }.getOrNull()

    val defaultOptions = listOf(
        Option("showTipsOnStartup", "false"),
        Option("confirmExit", "false")
    )
    val options =
        settings?.components?.find { it.name == "GeneralSettings" }
            ?.options
            ?.filterNot { it.name !in defaultOptions.map { it.name } }
            ?: defaultOptions

    val defaultRegistryEntries = listOf(
        Entry("ide.experimental.ui", "true"),
        Entry("packagesearch.plugin.debug.logging", "true"),
    )
    val registry = settings?.components?.find { it.name == "Registry" }
        ?.entries
        ?.filter { it.key !in defaultRegistryEntries.map { it.key } }
        ?.let { it + defaultRegistryEntries }
        ?: defaultRegistryEntries

    writeText(
        xml.encodeToString(
            Application(
                listOf(
                    Component("GeneralSettings", options = options),
                    Component("Registry", entries = registry)
                )
            )
        )
    )
}

fun File.patchLogFile(pluginId: String) {
    val debugLog = kotlin.runCatching { xml.decodeFromString<LogSettings>(readText()) }
        .getOrNull()

    val packageSearchCategory = Category("#" + pluginId, "TRACE")
    val debugLogCategories = debugLog?.components?.find { it.name == "Log.Categories" }
        ?.content
        ?.removePrefix(CDATA_PREFIX)
        ?.removeSuffix(CDATA_SUFFIX)
        ?.let { Json.decodeFromString<DebugLog>(it) }
        ?.categories
        ?.filter { it.category != pluginId }
        ?.let { it + packageSearchCategory }
        ?: listOf(packageSearchCategory)

    val encodedXml = xml.encodeToString(
        LogSettings(
            listOf(
                DebugComponent(
                    name = "Logs.Categories",
                    content = CDATA_PREFIX + Json.encodeToString(DebugLog(debugLogCategories)) + CDATA_SUFFIX
                )
            )
        )
    )
    writeText(
        encodedXml
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    )
}

val TaskProvider<PrepareSandboxTask>.registryTextFile
    get() = flatMap { it.configDir }
        .map { Paths.get(it).resolve("early-access-registry.txt").toFile() }

val TaskProvider<PrepareSandboxTask>.lafFile
    get() = flatMap { it.configDir }
        .map { Paths.get(it).resolve("options/laf.xml").toFile() }

val TaskProvider<PrepareSandboxTask>.settingsFile
    get() = flatMap { it.configDir }
        .map { Paths.get(it).resolve("options/ide.general.xml").toFile() }

val TaskProvider<PrepareSandboxTask>.logCategoriesFile
    get() = flatMap { it.configDir }
        .map { Paths.get(it).resolve("options/log-categories.xml").toFile() }