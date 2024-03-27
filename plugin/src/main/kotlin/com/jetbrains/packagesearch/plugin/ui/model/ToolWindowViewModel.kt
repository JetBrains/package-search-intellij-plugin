package com.jetbrains.packagesearch.plugin.ui.model

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.PackageSearchBundle.message
import com.jetbrains.packagesearch.plugin.core.utils.isProjectImportingFlow
import com.jetbrains.packagesearch.plugin.core.utils.smartModeFlow
import com.jetbrains.packagesearch.plugin.ui.bridge.openLinkInBrowser
import com.jetbrains.packagesearch.plugin.ui.model.tree.TreeViewModel
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchSettingsService
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.splitpane.SplitPaneState

@Service(Level.PROJECT)
class ToolWindowViewModel(private val project: Project, private val viewModelScope: CoroutineScope) {


    val firstSplitPaneState = mutableStateOf(
        SplitPaneState(
            initialPositionPercentage = project.PackageSearchSettingsService.firstSplitPanePositionFlow.value,
            moveEnabled = true,
        )
    )
    val secondSplitPaneState = mutableStateOf(
        SplitPaneState(
            initialPositionPercentage = project.PackageSearchSettingsService.secondSplitPanePositionFlow.value,
            moveEnabled = true,
        )
    )

    init {
        snapshotFlow { firstSplitPaneState.value.positionPercentage }
            .onEach { project.PackageSearchSettingsService.firstSplitPanePositionFlow.value = it }
            .flowOn(Dispatchers.Main)
            .launchIn(viewModelScope)

        snapshotFlow { secondSplitPaneState.value.positionPercentage }
            .onEach { project.PackageSearchSettingsService.secondSplitPanePositionFlow.value = it }
            .flowOn(Dispatchers.Main)
            .launchIn(viewModelScope)
    }

    fun openLinkInBrowser(url: String) {
        viewModelScope.openLinkInBrowser(url)
    }

    val isInfoPanelOpen
        get() = project.PackageSearchSettingsService.isInfoPanelOpenFlow

    private val easterEggMessage =
        message("packagesearch.toolwindow.loading.easterEgg.${Random.nextInt(0, 5)}")
            .takeIf { Random.nextInt(0, 20) >= 19 }

    val toolWindowState = combine(
        project.PackageSearchProjectService.packagesBeingDownloadedFlow,
        combine(
            project.isProjectImportingFlow,
            project.PackageSearchProjectService.isProjectExecutingSyncStateFlow
        ) { isProjectImporting, isProjectSyncing -> isProjectImporting || isProjectSyncing },
        project.service<TreeViewModel>()
            .treeStateFlow
            .map { !it.isEmpty() }
            .debounce(250.milliseconds),
        project.smartModeFlow,
    ) { _, isProjectSyncing, isTreeReady, isSmartMode ->
        when {
            isTreeReady -> PackageSearchToolWindowState.Ready

            !isSmartMode -> PackageSearchToolWindowState.Loading(
                message = easterEggMessage ?: message("packagesearch.toolwindow.loading.dumbMode")
            )

            isProjectSyncing -> PackageSearchToolWindowState.Loading(
                message = easterEggMessage ?: message("packagesearch.toolwindow.loading.syncing")
            )
//            Commented to mitigate PKGS-1389 "dowloading packages" UI does not reflect if packages
//            are really being downloaded or not | https://youtrack.jetbrains.com/issue/PKGS-1389
//            packagesBeingDownloaded -> PackageSearchToolWindowState.Loading(
//                message = easterEggMessage ?: message("packagesearch.toolwindow.loading.downloading")
//            )

            else -> PackageSearchToolWindowState.NoModules
        }
    }
        .retry(5)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = PackageSearchToolWindowState.Loading(message = easterEggMessage)
        )

}

