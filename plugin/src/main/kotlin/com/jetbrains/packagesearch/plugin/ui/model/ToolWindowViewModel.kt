package com.jetbrains.packagesearch.plugin.ui.model

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.PackageSearchBundle.message
import com.jetbrains.packagesearch.plugin.core.utils.smartModeFlow
import com.jetbrains.packagesearch.plugin.ui.bridge.openLinkInBrowser
import com.jetbrains.packagesearch.plugin.ui.model.tree.TreeViewModel
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Service(Level.PROJECT)
class ToolWindowViewModel(project: Project) : Disposable {

    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob())

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
            .debounce(250.milliseconds),
        project.smartModeFlow
    ) { packagesBeingDownloaded, isProjectSyncing, isTreeReady, isSmartMode ->
        when {
            isTreeReady -> PackageSearchToolWindowState.Ready

            !isSmartMode -> PackageSearchToolWindowState.Loading(
                message = easterEggMessage ?: message("packagesearch.toolwindow.loading.dumbMode")
            )

            isProjectSyncing -> PackageSearchToolWindowState.Loading(
                message = easterEggMessage ?: message("packagesearch.toolwindow.loading.syncing")
            )

            packagesBeingDownloaded -> PackageSearchToolWindowState.Loading(
                message = easterEggMessage ?: message("packagesearch.toolwindow.loading.downloading")
            )

            else -> PackageSearchToolWindowState.NoModules
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = PackageSearchToolWindowState.Loading(message = easterEggMessage)
        )

    override fun dispose() {
        viewModelScope.cancel()
    }
}

