package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.ui.JBColor
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.services.ModulesState.Loading
import com.jetbrains.packagesearch.plugin.services.ModulesState.NoModules
import com.jetbrains.packagesearch.plugin.services.ModulesState.Ready
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.asTree
import com.jetbrains.packagesearch.plugin.ui.bridge.openLinkInBrowser
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.IndeterminateHorizontalProgressBar
import org.jetbrains.jewel.Link
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.tree.rememberTreeState
import org.jetbrains.jewel.painterResource
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme

@Composable
fun PackageSearchToolwindow(isInfoBoxOpen: Boolean) {

    val backgroundColor by remember(IntUiTheme.isDark) { mutableStateOf(JBColor.PanelBackground.toComposeColor()) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(top = 2.dp, bottom = 0.dp, start = 2.dp, end = 2.dp),
    ) {
        val modulesState by LocalPackageSearchService.current.moduleData.collectAsState()
        when (val moduleProvider = modulesState) {
            is Loading -> IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth())
            is NoModules -> NoModulesFound()
            is Ready -> {
                LocalIsActionPerformingState.current.value = ActionState(false)
                val treeState = rememberTreeState()
                val (tree, nodesToOpen) =
                    moduleProvider.moduleData.asTree()
                var knownNodes: Set<PackageSearchModule.Identity> by remember { mutableStateOf(emptySet()) }
                remember(nodesToOpen) {
                    val result = nodesToOpen - knownNodes
                    treeState.openNodes(result.toList())
                    knownNodes = result
                }

                PackageSearchPackagePanel(
                    isInfoBoxOpen = isInfoBoxOpen,
                    tree = tree,
                    state = treeState,
                )
            }
        }
    }
}

@Composable
fun NoModulesFound() {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LabelInfo("No supported modules were found.")
        Row {
            LabelInfo("Try ")
            val packageSearchService = LocalPackageSearchService.current
            val scope = rememberCoroutineScope()
            var isEnabled by remember { mutableStateOf(true) }
            Link(
                enabled = isEnabled,
                resourceLoader = LocalResourceLoader.current,
                text = "refreshing",
                onClick = {
                    isEnabled = false
                    val allManagers = ExternalSystemApiUtil.getAllManagers()
                    val resultChannel = Channel<Unit>()
                    allManagers.map {
                        ImportSpecBuilder(packageSearchService.project, it.systemId)
                            .callback(object : ExternalProjectRefreshCallback {
                                val hasEmitted = AtomicBoolean(false)
                                override fun onSuccess(externalProject: DataNode<ProjectData>?) {
                                    if (hasEmitted.compareAndSet(false, true)) {
                                        resultChannel.trySend(Unit)
                                    }
                                }

                                override fun onFailure(errorMessage: String, errorDetails: String?) {
                                    if (hasEmitted.compareAndSet(false, true)) {
                                        resultChannel.trySend(Unit)
                                    }
                                }
                            })
                    }
                        .forEach { ExternalSystemUtil.refreshProjects(it) }
                    var refreshCount = 0
                    resultChannel.consumeAsFlow()
                        .onEach {
                            refreshCount++
                            if (refreshCount == allManagers.size) {
                                isEnabled = true
                                resultChannel.close()
                            }
                        }
                        .launchIn(scope)
                },
            )
            LabelInfo(" external projects")
        }
        Row {
            Icon(
                painter = painterResource("icons/intui/question.svg", LocalResourceLoader.current),
                modifier = Modifier.size(16.dp).padding(end = 4.dp),
                contentDescription = null,
            )
            Link(
                resourceLoader = LocalResourceLoader.current,
                text = "Learn more",
                onClick = { openLinkInBrowser("https://www.jetbrains.com/help/idea/package-search.html") },
            )
        }
    }
}
