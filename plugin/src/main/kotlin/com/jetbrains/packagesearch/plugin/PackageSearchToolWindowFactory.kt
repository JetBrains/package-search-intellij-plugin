package com.jetbrains.packagesearch.plugin;

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.actionSystem.ActionManager
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
import com.jetbrains.packagesearch.plugin.utils.PackageSearchUIStateService
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.packagesearch.plugin.services.PackageSearchUIStateService


inline fun <T> Iterable<T>.applyOnEach(action: T.() -> Unit) =
    forEach { it.action() }

class PackageSearchToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        val composeTunnel = project.PackageSearchUIStateService
        val apiClient = IntelliJApplication.PackageSearchApiClientService.client
        val isActionPerforming = mutableStateOf(false)

        val actionManager: ActionManager = ActionManager.getInstance()
        toolWindow.asSafely<ToolWindowEx>()?.setTabActions(
            actionManager.getAction("com.jetbrains.packagesearch.plugin.utils.PKGSInfoWindowAction")
        )

        toolWindow.addComposeTab("UX") {
            CompositionLocalProvider(
                LocalProjectService provides project.PackageSearchProjectService,
                LocalProjectCoroutineScope provides project.PackageSearchProjectService.coroutineScope,
                LocalPackageSearchUIStateService provides composeTunnel,
            ) {
                PackageSearchToolWindows(apiClient, isActionPerforming)
            }
        }


    }
}


val LocalProjectService = staticCompositionLocalOf<PackageSearchProjectService> {
    error("No ProjectService provided")
}
val LocalPackageSearchUIStateService =
    staticCompositionLocalOf<PackageSearchUIStateService> { error("No PackageSearchUIStateService provided") }

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
