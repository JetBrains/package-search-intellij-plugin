package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.utils.availableExtensionsFlow
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.openLinkInBrowser
import com.jetbrains.packagesearch.plugin.utils.collectAsState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link

@Composable
fun NoModulesFound() {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LabelInfo("No supported modules were found.")
        val allManagers: List<ExternalSystemManager<*, *, *, *, *>> by ExternalSystemManager.EP_NAME
            .availableExtensionsFlow
            .collectAsState()
        if (allManagers.isNotEmpty()) {
            Row {
                LabelInfo("Try ")
                val packageSearchService = LocalPackageSearchService.current
                val scope = rememberCoroutineScope()

                var isEnabled by remember { mutableStateOf(true) }
                Link(
                    enabled = isEnabled,
                    text = "refreshing",
                    onClick = {
                        isEnabled = false
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
        }
        LearnMoreLink()
    }
}

@Composable
fun LearnMoreLink() {
    Row {
        Icon(
            resource = "icons/intui/question.svg",
            modifier = Modifier.size(16.dp).padding(end = 4.dp),
            contentDescription = null,
            tint = JewelTheme.globalColors.infoContent,
            iconClass = IconProvider::class.java
        )
        Link(
            text = "Learn more",
            onClick = { openLinkInBrowser("https://www.jetbrains.com/help/idea/package-search.html") },
        )
    }
}