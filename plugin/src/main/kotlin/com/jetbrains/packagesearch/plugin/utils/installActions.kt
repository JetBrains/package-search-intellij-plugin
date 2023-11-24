package com.jetbrains.packagesearch.plugin.utils

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.util.asSafely
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.ui.model.ToolWindowViewModel

internal fun ToolWindow.installActions(project: Project) {
        val toggleOnlyStableAction = object : ToggleAction(
            PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyStable"),
            PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyStable.description"),
            AllIcons.Actions.PreviewDetails,
        ) {
            override fun isSelected(e: AnActionEvent) =
                project.PackageSearchProjectService.stableOnlyStateFlow.value

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                project.PackageSearchProjectService.stableOnlyStateFlow.value = state
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }
        val toggleInfoboxAction = object : ToggleAction(
            PackageSearchBundle.message("packagesearch.actions.showDetails.text"),
            PackageSearchBundle.message("packagesearch.actions.showDetails.description"),
            AllIcons.Actions.PreviewDetails,
        ) {
            override fun isSelected(e: AnActionEvent) =
                project.service<ToolWindowViewModel>().isInfoPanelOpen.value

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                project.service<ToolWindowViewModel>().isInfoPanelOpen.value = state
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }

        asSafely<ToolWindowEx>()
            ?.setAdditionalGearActions(DefaultActionGroup(toggleInfoboxAction, toggleOnlyStableAction))

        asSafely<ToolWindowEx>()?.setTitleActions(listOf(toggleInfoboxAction))
    }
