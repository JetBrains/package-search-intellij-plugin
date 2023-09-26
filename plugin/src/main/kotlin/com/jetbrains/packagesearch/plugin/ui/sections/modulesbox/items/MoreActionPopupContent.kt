package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items

import ai.grazie.utils.mpp.UUID
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.ui.ActionState
import com.jetbrains.packagesearch.plugin.ui.LocalGlobalPopupIdState
import com.jetbrains.packagesearch.plugin.ui.LocalIsActionPerformingState
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import com.jetbrains.packagesearch.plugin.ui.model.PackageGroup
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.Divider
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.bridge.SwingBridgeService
import org.jetbrains.jewel.bridge.retrieveStatelessIcon
import org.jetbrains.jewel.foundation.onHover
import org.jetbrains.jewel.intui.standalone.IntUiTheme
import org.jetbrains.jewel.styling.LocalLazyTreeStyle

@Composable
internal fun DeclaredPackageMoreActionPopup(
    group: PackageGroup.Declared,
    packageSearchDeclaredPackage: PackageSearchDeclaredPackage,
    borderColor: Color = remember(IntelliJTheme.isDark) { pickComposeColorFromLaf("OnePixelDivider.background") },
) {
    val context = LocalPackageSearchService.current
    val popupOpenStatus = LocalGlobalPopupIdState.current
    val svgLoader = service<SwingBridgeService>().svgLoader
    val service = LocalPackageSearchService.current
    val isActionPerforming = LocalIsActionPerformingState.current

    Column(
        Modifier
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val isDeleteHovered = remember { mutableStateOf(false) }
        Row(
            Modifier
                .fillMaxWidth()
                .hoverBackground(isDeleteHovered)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = !isActionPerforming.value.isPerforming
                ) {
                    deleteAction(
                        isActionPerforming,
                        context,
                        group,
                        packageSearchDeclaredPackage,
                        popupOpenStatus,
                        service
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            val removeIcon by retrieveStatelessIcon(
                "expui/general/delete.svg",
                svgLoader,
                IntUiTheme.iconData
            ).getPainter(LocalResourceLoader.current)
            Icon(removeIcon, contentDescription = null)
            Text(text = "Remove")
        }
        group.module.buildFilePath?.let {
            Divider(color = borderColor)
            val isGoToSourceHovered = remember { mutableStateOf(false) }
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(it.absolutePathString()) ?: return@let
            Row(
                Modifier
                    .fillMaxWidth()
                    .hoverBackground(isGoToSourceHovered)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        enabled = !isActionPerforming.value.isPerforming
                    ) {
                        goToSource(service, virtualFile, packageSearchDeclaredPackage)
                        popupOpenStatus.value = null
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                val removeIcon by retrieveStatelessIcon(
                    "actions/editSource.svg",
                    svgLoader,
                    IntUiTheme.iconData
                ).getPainter(LocalResourceLoader.current)
                Icon(removeIcon, contentDescription = null)
                Text(text = "Go to source")
            }
        }
    }
}

private fun goToSource(
    service: PackageSearchProjectService,
    virtualFile: VirtualFile,
    packageSearchDeclaredPackage: PackageSearchDeclaredPackage,
) {
    service.coroutineScope.launch(Dispatchers.EDT) {
        FileEditorManager
            .getInstance(service.project)
            .openFileEditor(
                OpenFileDescriptor(
                    service.project,
                    virtualFile,
                    packageSearchDeclaredPackage.declarationIndexes.declarationStartIndex
                ),
                true
            )
    }
}

private fun deleteAction(
    isActionPerforming: MutableState<ActionState>,
    context: PackageSearchProjectService,
    group: PackageGroup.Declared,
    packageSearchDeclaredPackage: PackageSearchDeclaredPackage,
    popupOpenStatus: MutableState<String?>,
    service: PackageSearchProjectService,
) {
    val id = UUID.random().text
    isActionPerforming.value = ActionState(true, id)
    context.coroutineScope.launch {
        group.dependencyManager.removeDependency(
            context,
            packageSearchDeclaredPackage.getRemoveData(),
        )
    }.invokeOnCompletion {
        it?.let {
            System.err.println(it.stackTraceToString())
        }
        popupOpenStatus.value = null
    }
    service.coroutineScope.launch {
        delay(5.seconds)
        if (isActionPerforming.value.actionId == id) {
            System.err.println("Remove action has been cancelled due a time out")
            isActionPerforming.value = ActionState(false)
        }
    }
}

@Composable
private fun Modifier.hoverBackground(
    hoveredState: MutableState<Boolean>,
    hoveredColor: Color = LocalLazyTreeStyle.current.colors.elementBackgroundSelectedFocused,
    unhoveredColor: Color = Color.Unspecified,
) = onHover { hovered -> hoveredState.value = hovered }
    .clip(shape = RoundedCornerShape(4.dp))
    .background(if (hoveredState.value) hoveredColor else unhoveredColor)
    .padding(4.dp)
