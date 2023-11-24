package com.jetbrains.packagesearch.plugin.ui.model

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.PackageSearchBundle.message
import com.jetbrains.packagesearch.plugin.ui.bridge.openLinkInBrowser
import com.jetbrains.packagesearch.plugin.ui.model.tree.TreeViewModel
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@Service(Level.PROJECT)
class ToolWindowViewModel(project: Project, private val viewModelScope: CoroutineScope) {
    fun openLinkInBrowser(url: String) {
        viewModelScope.openLinkInBrowser(url)
    }

    val isInfoPanelOpen = MutableStateFlow(false)

    private val easterEggMessage =
        message("packagesearch.toolwindow.loading.easterEgg.${Random.nextInt(0, 5)}")
            .takeIf { Random.nextInt(0, 20) >= 19 }

    val toolWindowState = combine(
        project.PackageSearchProjectService.packagesBeingDownloadedFlow,
        project.isProjectSyncing,
        project.service<TreeViewModel>()
            .tree
            .map { !it.isEmpty() }
            .debounce(250.milliseconds)
    ) { packagesBeingDownloaded, isProjectSyncing, isTreeReady ->
        when {
            isTreeReady ->
                PackageSearchToolWindowState.Ready
            packagesBeingDownloaded ->
                PackageSearchToolWindowState.Loading(
                    message = easterEggMessage ?: message("packagesearch.toolwindow.loading.downloading")
                )
            isProjectSyncing ->
                PackageSearchToolWindowState.Loading(
                    message = easterEggMessage ?: message("packagesearch.toolwindow.loading.syncing")
                )
            else -> PackageSearchToolWindowState.NoModules
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = PackageSearchToolWindowState.Loading(
                message = easterEggMessage ?: message("packagesearch.toolwindow.loading.downloading")
            )
        )

}

sealed interface PackageSearchToolWindowState {
    data class Loading(val message: String) : PackageSearchToolWindowState
    data object Ready : PackageSearchToolWindowState
    data object NoModules : PackageSearchToolWindowState
}