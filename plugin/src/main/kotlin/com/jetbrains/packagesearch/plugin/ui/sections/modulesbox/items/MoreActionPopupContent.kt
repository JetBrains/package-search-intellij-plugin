package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items

import ai.grazie.utils.mpp.UUID
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.InstallPackageData
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.ui.ActionState
import com.jetbrains.packagesearch.plugin.ui.ActionType
import com.jetbrains.packagesearch.plugin.ui.LocalGlobalPopupIdState
import com.jetbrains.packagesearch.plugin.ui.LocalIsActionPerformingState
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import com.jetbrains.packagesearch.plugin.utils.logDebug
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.LocalLazyTreeStyle
import org.jetbrains.packagesearch.api.v3.ApiPackage

@Composable
internal fun DeclaredPackageMoreActionPopup(
    dependencyManager: PackageSearchDependencyManager,
    module: PackageSearchModule,
    packageSearchDeclaredPackage: PackageSearchDeclaredPackage,
    borderColor: Color = remember(JewelTheme.isDark) { pickComposeColorFromLaf("OnePixelDivider.background") },
    backgroundColor: Color = remember(JewelTheme.isDark) { pickComposeColorFromLaf("PopupMenu.background") },
    onDismissRequest: () -> Unit = {},
) {
    val context = LocalPackageSearchService.current
    val popupOpenStatus = LocalGlobalPopupIdState.current
    val service = LocalPackageSearchService.current
    val isActionPerforming = LocalIsActionPerformingState.current

    Column(
        Modifier
            .background(backgroundColor)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val isDeleteHovered = remember { mutableStateOf(false) }
        module.buildFilePath?.let {
            val isGoToSourceHovered = remember { mutableStateOf(false) }
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(it.absolutePathString()) ?: return@let
            Row(
                Modifier
                    .fillMaxWidth()
                    .hoverBackground(isGoToSourceHovered)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        enabled = isActionPerforming.value != null
                    ) {
                        goToSource(service, virtualFile, packageSearchDeclaredPackage)
                        popupOpenStatus.value = null
                        onDismissRequest()
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Icon("general/locate.svg", contentDescription = null, AllIcons::class.java)
                Text(text = "Go to source")
            }
            Divider(orientation = Orientation.Horizontal, color = borderColor)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .hoverBackground(isDeleteHovered)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = isActionPerforming.value != null
                ) {
                    deleteAction(
                        isActionPerforming,
                        context,
                        dependencyManager = dependencyManager,
                        packageSearchDeclaredPackage,
                        popupOpenStatus,
                        service
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Icon("expui/general/delete.svg", contentDescription = null, AllIcons::class.java)
            Text(text = PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.remove.text"))
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
    isActionPerforming: MutableState<ActionState?>,
    context: PackageSearchProjectService,
    dependencyManager: PackageSearchDependencyManager,
    packageSearchDeclaredPackage: PackageSearchDeclaredPackage,
    popupOpenStatus: MutableState<String?>,
    service: PackageSearchProjectService,
) {
    val id = UUID.random().text
    isActionPerforming.value = ActionState(true, ActionType.REMOVE, id)
    context.coroutineScope.launch {
        dependencyManager.removeDependency(
            context,
            packageSearchDeclaredPackage.getRemoveData(),
        )
    }.invokeOnCompletion {
        it?.let {
            logDebug("Failed action Remove for package: ${packageSearchDeclaredPackage.id}", it)
        }
        popupOpenStatus.value = null
    }
    service.coroutineScope.launch {
        delay(5.seconds)
        if (isActionPerforming.value?.actionId == id) {
            logDebug("Timeout action Remove for package: ${packageSearchDeclaredPackage.id}")
            isActionPerforming.value = null
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

@Composable
fun RemotePackageMorePopupContent(
    apiPackage: ApiPackage,
    selectedModule: PackageSearchModuleData,
    onDismissRequest: () -> Unit,
) {
    val context = LocalPackageSearchService.current
    val popupOpenStatus = LocalGlobalPopupIdState.current
    val service = LocalPackageSearchService.current
    val isActionPerforming = LocalIsActionPerformingState.current
    val onlyStable = LocalIsOnlyStableVersions.current.value
    val scrollstate = rememberScrollState()
    Box {
        Column(
            modifier = Modifier.verticalScroll(scrollstate),
        ) {
            when (val module = selectedModule.module) {
                is PackageSearchModule.WithVariants -> {
                    module.variants.forEach {
                        val isActionHovered = remember { mutableStateOf(false) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .hoverBackground(isActionHovered)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    enabled = isActionPerforming.value != null
                                ) {
                                    val packageInstallData = it.value.getInstallData(
                                        apiPackage,
                                        apiPackage.getLatestVersion(onlyStable)
                                    )
                                    addAction(
                                        isActionPerforming,
                                        context,
                                        selectedModule.dependencyManager,
                                        packageInstallData,
                                        popupOpenStatus,
                                        service
                                    )
                                    onDismissRequest()
                                },
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                PackageSearchBundle.message(
                                    "packagesearch.ui.toolwindow.actions.addTo.text",
                                    it.value.name
                                )
                            )
                        }
                    }

                }

                is PackageSearchModule.Base -> {
                    val isActionHovered = remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .hoverBackground(isActionHovered)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                enabled = isActionPerforming.value == null
                            ) {
                                val installData = module.getInstallData(
                                    apiPackage,
                                    apiPackage.getLatestVersion(onlyStable)
                                )
                                addAction(
                                    isActionPerforming,
                                    context,
                                    selectedModule.dependencyManager,
                                    installData,
                                    popupOpenStatus,
                                    service
                                )
                            },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.add.text"))
                    }

                }
            }
        }


        Box(modifier = Modifier.matchParentSize()) {
            VerticalScrollbar(
                rememberScrollbarAdapter(scrollstate),
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
            )
        }
    }


}

private fun addAction(
    isActionPerforming: MutableState<ActionState?>,
    context: PackageSearchProjectService,
    dependencyManager: PackageSearchDependencyManager,
    installPackageData: InstallPackageData,
    popupOpenStatus: MutableState<String?>,
    service: PackageSearchProjectService,
) {
    val id = UUID.random().text
    isActionPerforming.value = ActionState(true, ActionType.ADD, id)
    context.coroutineScope.launch {
        dependencyManager.addDependency(
            context,
            installPackageData,
        )
    }.invokeOnCompletion {
        it?.let {
            logDebug("Error while adding dependency:\n$installPackageData", it)
        }
        popupOpenStatus.value = null
    }
    service.coroutineScope.launch {
        delay(5.seconds)
        if (isActionPerforming.value?.actionId == id) {
            logDebug("Add action has been cancelled due a time out")
            isActionPerforming.value = null
        }
    }
}