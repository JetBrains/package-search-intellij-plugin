package com.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.utils.packageSearchProjectDataPath
import com.jetbrains.packagesearch.plugin.ui.PackageSearchMetrics
import com.jetbrains.packagesearch.plugin.utils.PackageSearchLogger
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

@Service(Level.PROJECT)
class PackageSearchSettingsService(private val project: Project, private val coroutineScope: CoroutineScope) {

    private val settingsFile
        get() = project.packageSearchProjectDataPath / "settings.json"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun loadSettings() =
        kotlin.runCatching { json.decodeFromStream<Settings>(settingsFile.inputStream()) }
            .onSuccess { PackageSearchLogger.logDebug { "Settings loaded: \n${settingsFile.readText()}" } }
            .map { it.asSafe() }
            .onFailure { PackageSearchLogger.logDebug(throwable = it, message = "Failed to load settings") }
            .getOrElse { Settings() }

    private suspend fun Settings.save() =
        withContext(Dispatchers.IO) { settingsFile.writeText(json.encodeToString(this@save)) }

    val stableOnlyFlow: MutableStateFlow<Boolean>
    val installRepositoryIfNeededFlow: MutableStateFlow<Boolean>
    val isInfoPanelOpenFlow: MutableStateFlow<Boolean>
    val firstSplitPanePositionFlow: MutableStateFlow<Float>
    val secondSplitPanePositionFlow: MutableStateFlow<Float>

    init {

        val initialSettings = loadSettings()

        stableOnlyFlow = MutableStateFlow(initialSettings.onlyStable)
        installRepositoryIfNeededFlow = MutableStateFlow(initialSettings.installRepositoryIfNeeded)
        isInfoPanelOpenFlow = MutableStateFlow(initialSettings.isInfoPanelOpen)
        firstSplitPanePositionFlow = MutableStateFlow(initialSettings.firstSplitPanePosition.coerceIn(0f, 1f))
        secondSplitPanePositionFlow = MutableStateFlow(initialSettings.secondSplitPanePosition.coerceIn(0f, 1f))

        combine(
            stableOnlyFlow,
            installRepositoryIfNeededFlow,
            isInfoPanelOpenFlow,
            firstSplitPanePositionFlow,
            secondSplitPanePositionFlow,
        ) {
                stableOnly, installRepositoryIfNeeded, isInfoPanelOpen,
                firstSplitPanePosition, secondSplitPanePosition,
            ->
            Settings(
                onlyStable = stableOnly,
                installRepositoryIfNeeded = installRepositoryIfNeeded,
                isInfoPanelOpen = isInfoPanelOpen,
                firstSplitPanePosition = firstSplitPanePosition,
                secondSplitPanePosition = secondSplitPanePosition,
            )
        }
            .debounce(1.seconds)
            .onEach { it.save() }
            .launchIn(coroutineScope)
    }
}

@Serializable
private data class Settings(
    val onlyStable: Boolean = true,
    val installRepositoryIfNeeded: Boolean = true,
    val isInfoPanelOpen: Boolean = false,
    val firstSplitPanePosition: Float = PackageSearchMetrics.Splitpanes.firstSplitPositionPercentage,
    val secondSplitPanePosition: Float = PackageSearchMetrics.Splitpanes.secondSplitPositionPercentage,
) {
    fun asSafe() = copy(
        firstSplitPanePosition = firstSplitPanePosition.coerceIn(0f, 1f),
        secondSplitPanePosition = secondSplitPanePosition.coerceIn(0f, 1f),
    )
}