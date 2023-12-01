package com.jetbrains.packagesearch.plugin

import androidx.compose.runtime.CompositionLocalProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.jetbrains.packagesearch.plugin.ui.LocalComponentManager
import com.jetbrains.packagesearch.plugin.ui.PackageSearchToolwindow
import com.jetbrains.packagesearch.plugin.ui.panels.packages.PackageSearchTreeStyle
import com.jetbrains.packagesearch.plugin.ui.panels.packages.packageSearchGlobalColors
import com.jetbrains.packagesearch.plugin.ui.panels.packages.packageSearchTabStyle
import com.jetbrains.packagesearch.plugin.utils.installActions
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.foundation.LocalGlobalColors
import org.jetbrains.jewel.ui.component.styling.LocalDefaultTabStyle
import org.jetbrains.jewel.ui.component.styling.LocalLazyTreeStyle

class PackageSearchToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.installActions(project)
        toolWindow.addComposeTab(PackageSearchBundle.message("packagesearch.title.tab")) {
            CompositionLocalProvider(
                LocalComponentManager provides project,
                LocalGlobalColors provides packageSearchGlobalColors(),
                LocalDefaultTabStyle provides packageSearchTabStyle(),
                LocalLazyTreeStyle provides PackageSearchTreeStyle()
            ) {
                PackageSearchToolwindow()
            }
        }
    }
}

