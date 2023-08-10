package com.jetbrains.packagesearch.plugin.utils

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import org.jetbrains.packagesearch.plugin.services.InfoTabState

internal class PKGSInfoWindowAction : ToggleAction() {


    // don't use field inside AnAction (or his subclasses like ToggleAction)
    // because it will keep in memory the entire project
    private fun AnActionEvent.getInfoTabStateFlow() =
        project?.PackageSearchComposeTunnel?.infoTabStateFlow

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun setDefaultIcon(isDefaultIconSet: Boolean) {
        super.setDefaultIcon(isDefaultIconSet) //todo set the icon
    }

    override fun isSelected(e: AnActionEvent): Boolean =
        e.getInfoTabStateFlow()?.value is InfoTabState.Open

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (isSelected(e)) e.getInfoTabStateFlow()?.value = InfoTabState.Close
        else e.getInfoTabStateFlow()?.value = InfoTabState.Open
    }
}