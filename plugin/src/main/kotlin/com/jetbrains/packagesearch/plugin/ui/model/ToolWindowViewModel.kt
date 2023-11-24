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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Service(Level.PROJECT)
class ToolWindowViewModel(project: Project, private val viewModelScope: CoroutineScope) {
    fun openLinkInBrowser(url: String) {
        viewModelScope.openLinkInBrowser(url)
    }

    val isInfoPanelOpen = MutableStateFlow(false)
    val isLoading = MutableStateFlow(true)
    val loadingMessage:  MutableStateFlow<String?> = MutableStateFlow(null)

    init {
        project.service<TreeViewModel>()
            .tree
            .map { it.isEmpty() }
            .onEach { isLoading.emit(it) }
            .launchIn(viewModelScope)

        combine(
            project.PackageSearchProjectService.packagesBeingDownloadedFlow,
            project.isProjectSyncing
        ) { packagesBeingDownloaded, isProjectSyncing ->
            // with a 5% chance, show an easter egg
            val easterEgg = Random.nextInt(0, 20) >= 19
            when {
                easterEgg -> message("packagesearch.toolwindow.loading.easterEgg.${Random.nextInt(0, 5)}")
                isProjectSyncing -> message("packagesearch.toolwindow.loading.syncing")
                packagesBeingDownloaded -> message("packagesearch.toolwindow.loading.downloading")
                else -> null
            }
        }
            .onEach { loadingMessage.emit(it) }
            .launchIn(viewModelScope)
    }

}