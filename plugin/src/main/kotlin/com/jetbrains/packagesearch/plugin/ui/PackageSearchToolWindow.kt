package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.ui.JBColor
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.services.ModulesState.Loading
import com.jetbrains.packagesearch.plugin.services.ModulesState.NoModules
import com.jetbrains.packagesearch.plugin.services.ModulesState.Ready
import com.jetbrains.packagesearch.plugin.ui.bridge.asTree
import com.jetbrains.packagesearch.plugin.utils.logWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.rememberTreeState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IndeterminateHorizontalProgressBar

@Composable
fun PackageSearchToolwindow(isInfoBoxOpen: Boolean) {

    val backgroundColor by remember(JewelTheme.isDark) { mutableStateOf(JBColor.PanelBackground.toComposeColor()) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(top = 2.dp, bottom = 0.dp, start = 2.dp, end = 2.dp),
    ) {
        val modulesState by LocalPackageSearchService.current.moduleData.collectAsState()
        val packagesListState = rememberSelectableLazyListState()
        val scope= rememberCoroutineScope()
        when (val moduleProvider = modulesState) {
            is Loading -> IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth())
            is NoModules -> NoModulesFound()
            is Ready -> {
                var isActionPerforming by LocalIsActionPerformingState.current
                isActionPerforming?.let {
                    if (it.actionType == ActionType.ADD)
                        scope.launch {
                            packagesListState.lazyListState.scrollToItem(0)
                        }
                }
                isActionPerforming = null
                val treeState = rememberTreeState()
                val tree = moduleProvider.moduleData.asTree()
                if (tree.isEmpty()) {
                    if (moduleProvider.moduleData.isNotEmpty()) {
                        val packageSearchProjectService = LocalPackageSearchService.current
                        remember(Unit) {
                            logWarn("Modules are present but tree is empty. Something fishy happened...")
                            ExternalSystemManager.EP_NAME.extensionList
                                .map {
                                    ImportSpecBuilder(packageSearchProjectService.project, it.systemId)
                                        .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
                                }
                                .asFlow()
                                .onEach { ExternalSystemUtil.refreshProjects(it) }
                                .flowOn(Dispatchers.EDT)
                                .launchIn(packageSearchProjectService.coroutineScope)
                        }
                        IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth())
                    } else NoModulesFound()
                    return
                }

                var knownNodes: Set<PackageSearchModule.Identity> by remember { mutableStateOf(emptySet()) }

                val nodesToOpen = tree.walkBreadthFirst()
                    .filterIsInstance<Tree.Element.Node<PackageSearchModuleData>>()
                    .map { it.data.module.identity }
                    .toSet()

                remember(nodesToOpen) {
                    val result = nodesToOpen - knownNodes
                    treeState.openNodes(result.toList())
                    knownNodes = result
                }

                PackageSearchPackagePanel(
                    packagesListState = packagesListState,
                    isInfoBoxOpen = isInfoBoxOpen,
                    tree = tree,
                    state = treeState,
                )
            }
        }
    }
}

