package com.jetbrains.packagesearch.plugin

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.jetbrains.packagesearch.plugin.ui.PackageSearchTheme
import com.jetbrains.packagesearch.plugin.ui.PackageSearchToolwindow
import com.jetbrains.packagesearch.plugin.utils.installActions
import org.jetbrains.jewel.bridge.addComposeTab

class PackageSearchToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.installActions(project)
        toolWindow.addComposeTab(PackageSearchBundle.message("packagesearch.title.tab")) {
            PackageSearchTheme(project) {
                PackageSearchToolwindow()
            }
        }
    }
}
