package org.jetbrains.packagesearch.plugin;

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.plugin.services.ModulesState
import org.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import java.io.File

inline fun <T> Iterable<T>.applyOnEach(action: T.() -> Unit) =
    forEach { it.action() }

class PackageSearchToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        project.PackageSearchProjectService.coroutineScope.launch {
            project.PackageSearchProjectService.modules.collect {
                println(it)
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
        ComposePanel().apply { setContent(content) },
        title,
        isLockable
    )
)

