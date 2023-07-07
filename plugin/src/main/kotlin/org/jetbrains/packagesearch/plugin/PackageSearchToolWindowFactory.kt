package org.jetbrains.packagesearch.plugin;

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.jetbrains.jewel.foundation.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.tree.buildTree
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import org.jetbrains.packagesearch.plugin.services.ModulesState
import org.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService

class PackageSearchToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        project.PackageSearchProjectService.modules
            .filterIsInstance<ModulesState.Ready>()
            .map { it.modules }
            .map { it.generateData() }
    }
}

fun List<PackageSearchModuleData>.generateData() =
    buildTree {
        groupBy { it::class.qualifiedName }
            .values
            .forEach {
                val sortedItems = it.sortedBy { it.module.identity.group }
                sortedItems.filter { it.module.identity.path.split(":").isEmpty() }
                    .forEach { addData(sortedItems, it) }
            }
    }

fun TreeGeneratorScope<PackageSearchModuleData>.addData(
    sortedItems: List<PackageSearchModuleData>,
    currentData: PackageSearchModuleData
) {
    val children = sortedItems
        .filter { it.module.identity.path.startsWith(currentData.module.identity.path) }
    if (children.isNotEmpty()) addNode(currentData, id = currentData.module) {
        children.forEach { addData(sortedItems.subList(sortedItems.indexOf(currentData) + 1, sortedItems.size), it) }
    }
    else addLeaf(currentData, id = currentData.module)
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

