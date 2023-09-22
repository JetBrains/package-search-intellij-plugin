@file:Suppress("DialogTitleCapitalization")

package com.jetbrains.packagesearch.plugin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.util.asSafely
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.registryFlow
import com.jetbrains.packagesearch.plugin.ui.ActionState
import com.jetbrains.packagesearch.plugin.ui.LocalGlobalPopupIdState
import com.jetbrains.packagesearch.plugin.ui.LocalInfoBoxPanelOpenState
import com.jetbrains.packagesearch.plugin.ui.LocalIsActionPerformingState
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchApiClient
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.LocalProjectCoroutineScope
import com.jetbrains.packagesearch.plugin.ui.PackageSearchToolwindow
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiPackageCache
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApplicationCachesService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.jewel.bridge.SwingBridgeTheme
import org.jetbrains.jewel.bridge.addComposeTab

class PackageSearchToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        System.setProperty("compose.swing.render.on.graphics", "true")
        val isInfoBoxOpen = mutableStateOf(false)
        val toggleOnlyStableAction = object : ToggleAction(
            PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyStable"),
            PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyStable.description"),
            AllIcons.Actions.PreviewDetails,
        ) {
            override fun isSelected(e: AnActionEvent) =
                project.PackageSearchProjectService.isStableOnlyVersions.value

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                project.PackageSearchProjectService.isStableOnlyVersions.value = state
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }
        val toggleInfoboxAction = object : ToggleAction(
            PackageSearchBundle.message("packagesearch.actions.showDetails.text"),
            PackageSearchBundle.message("packagesearch.actions.showDetails.description"),
            AllIcons.Actions.PreviewDetails,
        ) {
            override fun isSelected(e: AnActionEvent) = isInfoBoxOpen.value
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                isInfoBoxOpen.value = state
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }

        IntelliJApplication
            .registryFlow("packagesearch.package.details")
            .onEach {
                if (it) {
                    toolWindow.asSafely<ToolWindowEx>()
                        ?.setAdditionalGearActions(DefaultActionGroup(toggleInfoboxAction, toggleOnlyStableAction))
                    toolWindow.asSafely<ToolWindowEx>()?.setTitleActions(listOf(toggleInfoboxAction))
                } else {
                    isInfoBoxOpen.value = false
                    toolWindow.asSafely<ToolWindowEx>()
                        ?.setAdditionalGearActions(DefaultActionGroup(toggleOnlyStableAction))
                    toolWindow.asSafely<ToolWindowEx>()?.setTitleActions(emptyList())
                }
            }
            .flowOn(Dispatchers.EDT)
            .launchIn(project.PackageSearchProjectService.coroutineScope)

        toolWindow.addComposeTab(PackageSearchBundle.message("packagesearch.title.tab")) {
            val apiClient: PackageSearchApiPackageCache by IntelliJApplication.PackageSearchApplicationCachesService
                .apiPackageCache
                .collectAsState()
            SwingBridgeTheme {
                CompositionLocalProvider(
                    LocalPackageSearchService provides project.PackageSearchProjectService,
                    LocalProjectCoroutineScope provides project.PackageSearchProjectService.coroutineScope,
                    LocalPackageSearchApiClient provides apiClient,
                    LocalIsActionPerformingState provides mutableStateOf(ActionState(false)),
                    LocalInfoBoxPanelOpenState provides isInfoBoxOpen,
                    LocalIsOnlyStableVersions provides project.PackageSearchProjectService.isStableOnlyVersions,
                    LocalGlobalPopupIdState provides mutableStateOf(null),
                ) {
                    PackageSearchToolwindow(isInfoBoxOpen.value)
                }
            }
        }
    }
}