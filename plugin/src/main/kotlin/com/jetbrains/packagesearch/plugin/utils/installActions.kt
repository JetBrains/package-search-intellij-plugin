@file:Suppress("DialogTitleCapitalization")

package com.jetbrains.packagesearch.plugin.utils

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.util.asSafely
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.fus.PackageSearchFUSEvent
import javax.swing.Icon

internal fun ToolWindow.installActions(project: Project) {
    val toggleInstallRepository = ToggleAction(
        actionText = PackageSearchBundle.message("packagesearch.configuration.automatically.add.repositories.tip"),
        actionDescription = PackageSearchBundle.message("packagesearch.configuration.automatically.add.repositories"),
        isSelected = { project.PackageSearchSettingsService.installRepositoryIfNeededFlow.value },
        setSelected = { project.PackageSearchSettingsService.installRepositoryIfNeededFlow.value = it }
    )

    val toggleOnlyStableAction = ToggleAction(
        actionText = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyStable"),
        actionDescription = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyStable.description"),
        isSelected = { project.PackageSearchSettingsService.stableOnlyFlow.value },
        setSelected = {
            logFUSEvent(PackageSearchFUSEvent.OnlyStableToggle(it))
            project.PackageSearchSettingsService.stableOnlyFlow.value = it
        }
    )

    val toggleInfoboxAction = ToggleAction(
        actionText = PackageSearchBundle.message("packagesearch.actions.showDetails.text"),
        actionDescription = PackageSearchBundle.message("packagesearch.actions.showDetails.description"),
        isSelected = { project.PackageSearchSettingsService.isInfoPanelOpenFlow.value },
        setSelected = { project.PackageSearchSettingsService.isInfoPanelOpenFlow.value = it }
    )

    asSafely<ToolWindowEx>()
        ?.setAdditionalGearActions(
            DefaultActionGroup(
                toggleInfoboxAction,
                toggleOnlyStableAction,
                toggleInstallRepository
            )
        )

    asSafely<ToolWindowEx>()?.setTitleActions(listOf(toggleInfoboxAction))
}

private fun ToggleAction(
    actionText: String,
    actionDescription: String,
    icon: Icon = AllIcons.Actions.PreviewDetails,
    isSelected: () -> Boolean,
    setSelected: (Boolean) -> Unit,
) = object : ToggleAction(actionText, actionDescription, icon), DumbAware {
    override fun isSelected(e: AnActionEvent) = isSelected()
    override fun setSelected(e: AnActionEvent, state: Boolean) = setSelected(state)
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
