package com.jetbrains.packagesearch.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.jetbrains.packagesearch.plugin.ui.LocalGlobalPopupIdState
import com.jetbrains.packagesearch.plugin.ui.LocalIsActionPerformingState
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchApiClient
import com.jetbrains.packagesearch.plugin.ui.LocalProjectCoroutineScope
import com.jetbrains.packagesearch.plugin.ui.LocalProjectService
import com.jetbrains.packagesearch.plugin.ui.PackageSearchToolwindow
import com.jetbrains.packagesearch.plugin.ui.bridge.isLightTheme
import com.jetbrains.packagesearch.plugin.ui.lightThemeFlow
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiClientService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme

class PackageSearchToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        var isInfoBoxOpen by mutableStateOf(false)
        toolWindow.asSafely<ToolWindowEx>()?.setTitleActions(
            listOf(
                object : ToggleAction(
                    PackageSearchBundle.message("packagesearch.actions.showDetails.text"),
                    PackageSearchBundle.message("packagesearch.actions.showDetails.description"),
                    AllIcons.Actions.PreviewDetails
                ) {
                    override fun isSelected(e: AnActionEvent) = isInfoBoxOpen
                    override fun setSelected(e: AnActionEvent, state: Boolean) {
                        isInfoBoxOpen = state
                    }
                    override fun getActionUpdateThread() = ActionUpdateThread.BGT
                }
            )
        )
        toolWindow.addComposeTab("UX") {
            CompositionLocalProvider(
                LocalProjectService provides project.PackageSearchProjectService,
                LocalProjectCoroutineScope provides project.PackageSearchProjectService.coroutineScope,
                LocalPackageSearchApiClient provides IntelliJApplication.PackageSearchApiClientService.client,
                LocalIsActionPerformingState provides mutableStateOf(false),
                LocalIsOnlyStableVersions provides mutableStateOf(true),
                LocalGlobalPopupIdState provides mutableStateOf(null),
            ) {
                PackageSearchToolwindow(isInfoBoxOpen)
            }
        }
    }
}

private fun ToolWindow.addComposeTab(
    title: String,
    isLockable: Boolean = false,
    content: @Composable () -> Unit,
) = contentManager.addContent(
    contentManager.factory.createContent(
        /* component = */ ComposePanel().apply {
            setContent {
                val lightMode by IntelliJApplication.lightThemeFlow().collectAsState(isLightTheme())

                val swingCompat by remember { mutableStateOf(false) }
                val theme = if (!lightMode) IntUiTheme.darkThemeDefinition() else IntUiTheme.lightThemeDefinition()
                IntUiTheme(theme, swingCompat, content)
            }
        },
        /* displayName = */ title,
        /* isLockable = */ isLockable
    )
)
