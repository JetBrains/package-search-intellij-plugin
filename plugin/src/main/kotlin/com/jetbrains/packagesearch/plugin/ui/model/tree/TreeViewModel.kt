package com.jetbrains.packagesearch.plugin.ui.model.tree

import androidx.compose.foundation.lazy.LazyListState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApplicationCachesService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.jetbrains.jewel.foundation.lazy.tree.emptyTree

@Service(Level.PROJECT)
internal class TreeViewModel(project: Project) : Disposable {

    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob())

    val tree: StateFlow<Tree<TreeItemModel>> = combine(
        project.PackageSearchProjectService.modulesStateFlow,
        project.PackageSearchProjectService.stableOnlyStateFlow
    ) { modules, stableOnly ->
        modules.asTree(stableOnly)
    }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyTree())

    internal val lazyListState = LazyListState()
    internal val treeState = TreeState(SelectableLazyListState(lazyListState))

    val isOnline
        get() = IntelliJApplication.PackageSearchApplicationCachesService
            .apiPackageCache
            .isOnlineFlow

    fun expandAll() {
        treeState.openNodes = tree.value.walkBreadthFirst().map { it.id }.toSet()
    }

    fun collapseAll() {
        treeState.openNodes = emptySet()
    }

    override fun dispose() {
        viewModelScope.cancel()
    }

}

