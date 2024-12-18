package com.jetbrains.packagesearch.plugin.ui.model.tree

import androidx.compose.foundation.lazy.LazyListState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiClientService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchLogger
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchSettingsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.jetbrains.jewel.foundation.lazy.tree.emptyTree

@Service(Level.PROJECT)
internal class TreeViewModel(
    project: Project,
    viewModelScope: CoroutineScope,
) {

    val treeStateFlow: StateFlow<Tree<TreeItemModel>> = combine(
        project.PackageSearchProjectService.modulesStateFlow,
        project.PackageSearchSettingsService.stableOnlyFlow
    ) { modules, stableOnly ->
        modules.asTree(stableOnly)
    }
        .retry(5)
        .onEach { PackageSearchLogger.logDebug("${this::class.qualifiedName}#treeStateFlow") { it.print() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyTree())

    private fun Tree<TreeItemModel>.print(): String {
        val walkDepthFirst = walkDepthFirst().toList()
        if (walkDepthFirst.isEmpty()) return "Tree is empty"
        fun PackageSearchModule.Identity.printable() =
            "$group | $path | $projectDir"

        val treeString = walkDepthFirst.joinToString("\n") { " - " + it.data.id.printable() }
        return "Tree:\n$treeString"
    }

    internal val lazyListState = LazyListState()
    internal val treeState = TreeState(SelectableLazyListState(lazyListState))

    val isOnline
        get() = IntelliJApplication.PackageSearchApiClientService.client.onlineStateFlow

    fun expandAll() {
        treeState.openNodes = treeStateFlow.value.walkBreadthFirst().map { it.id }.toSet()
    }

    fun collapseAll() {
        treeState.openNodes = emptySet()
    }

}

