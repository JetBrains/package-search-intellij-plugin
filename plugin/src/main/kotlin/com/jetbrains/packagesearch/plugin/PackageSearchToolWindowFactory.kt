package com.jetbrains.packagesearch.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.awt.ComposePanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.util.asSafely
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.ui.PackageSearchToolWindows
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiClientService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import kotlinx.coroutines.CoroutineScope

class PackageSearchToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        val apiClient = IntelliJApplication.PackageSearchApiClientService.client
        val isActionPerforming = mutableStateOf(false)
        var isDetailsPanelOpen by mutableStateOf(false)
        toolWindow.asSafely<ToolWindowEx>()
            ?.setTitleActions(
                listOf(
                    object : ToggleAction(
                        PackageSearchBundle.message("packagesearch.actions.showDetails.text"),
                        PackageSearchBundle.message("packagesearch.actions.showDetails.description"),
                        AllIcons.Actions.PreviewDetails
                    ) {
                        override fun isSelected(e: AnActionEvent) = isDetailsPanelOpen
                        override fun setSelected(e: AnActionEvent, state: Boolean) {
                            isDetailsPanelOpen = state
                        }

                        override fun getActionUpdateThread() = ActionUpdateThread.BGT
                    }
                )
            )

        toolWindow.addComposeTab("UX") {
            CompositionLocalProvider(
                LocalProjectService provides project.PackageSearchProjectService,
                LocalProjectCoroutineScope provides project.PackageSearchProjectService.coroutineScope,
            ) {
                PackageSearchToolWindows(apiClient, isActionPerforming, isDetailsPanelOpen)
            }
        }


    }
}


val LocalProjectService = staticCompositionLocalOf<PackageSearchProjectService> {
    error("No ProjectService provided")
}

val LocalProjectCoroutineScope = staticCompositionLocalOf<CoroutineScope> {
    error("No ProjectCoroutineScope provided")
}

private fun ToolWindow.addComposeTab(
    title: String,
    isLockable: Boolean = false,
    content: @Composable () -> Unit,
) = contentManager.addContent(
    contentManager.factory.createContent(
        ComposePanel().apply { setContent(content) },
        title,
        isLockable
    )
)
