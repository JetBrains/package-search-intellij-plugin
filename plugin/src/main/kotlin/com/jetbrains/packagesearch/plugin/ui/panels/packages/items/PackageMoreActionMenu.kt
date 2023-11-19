package com.jetbrains.packagesearch.plugin.ui.panels.packages.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.ui.ActionState
import com.jetbrains.packagesearch.plugin.ui.PackageSearchMetrics
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.model.PackageGroup
import com.jetbrains.packagesearch.plugin.ui.panels.packages.addAction
import com.jetbrains.packagesearch.plugin.ui.panels.packages.addPackageToModule
import com.jetbrains.packagesearch.plugin.ui.panels.packages.deleteAction
import com.jetbrains.packagesearch.plugin.ui.panels.packages.goToSource
import kotlin.io.path.absolutePathString
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.packagesearch.api.v3.ApiPackage

fun MenuScope.DeclaredPackageMoreActionsMenu(
    dependencyManager: PackageSearchDependencyManager,
    module: PackageSearchModule,
    packageSearchDeclaredPackage: PackageSearchDeclaredPackage,
    service: PackageSearchProjectService,
    isActionPerformingState: MutableState<ActionState?>,
    popupOpenState: MutableState<String?>,
    onActionPerformed: (() -> Unit) = { },
) {
    module.buildFilePath?.let {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(it.absolutePathString()) ?: return@let
        selectableItem(
            selected = false,
            enabled = true,
            onClick = {
                goToSource(service, virtualFile, packageSearchDeclaredPackage)
                onActionPerformed()
            }
        ) {
            Row(
                modifier = Modifier.defaultMinSize(minWidth = PackageSearchMetrics.Popups.minWidth),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon("general/locate.svg", contentDescription = null, AllIcons::class.java)
                Text(text = "Go to source")
            }
        }
        passiveItem {
            Divider(Orientation.Horizontal)
        }
    }
    selectableItem(
        selected = false,
        enabled = true,
        onClick = {
            if (isActionPerformingState.value == null) {
                deleteAction(
                    context = service,
                    dependencyManager = dependencyManager,
                    packageSearchDeclaredPackage = packageSearchDeclaredPackage,
                    isActionPerforming = isActionPerformingState,
                    popupOpenStatus = popupOpenState,
                    service = service
                )
                onActionPerformed()
            }
        }
    ) {
        Row(
            modifier = Modifier.defaultMinSize(minWidth = PackageSearchMetrics.Popups.minWidth),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon("expui/general/delete.svg", contentDescription = null, AllIcons::class.java)
            Text(text = PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.remove.text"))
        }
    }
}


fun MenuScope.RemotePackageMoreActionsMenu(
    apiPackage: ApiPackage,
    group: PackageGroup.Remote,
    isActionPerformingState: MutableState<ActionState?>,
    service: PackageSearchProjectService,
    isOnlyStable: Boolean,
    onActionPerformed: (() -> Unit) = {},
) {
    when (group) {
        is PackageGroup.Remote.FromBaseModule -> {
            selectableItem(false,
                onClick = {
                    addAction(
                        isActionPerforming = isActionPerformingState,
                        dependencyManager = group.dependencyManager,
                        installPackageData = group.module.getInstallData(
                            apiPackage,
                            apiPackage.getLatestVersion(isOnlyStable)
                        ),
                        onActionComplete = { _ -> onActionPerformed() },
                        service = service
                    )
                }) {
                Text(
                    modifier = Modifier.defaultMinSize(PackageSearchMetrics.Popups.minWidth),
                    text = PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.add.text")
                )
            }
        }

        is PackageGroup.Remote.FromVariants -> {
            val defaultVariant = group.compatibleVariants.first { it.isPrimary }
            val groupVariants = group.compatibleVariants - defaultVariant
            val groupVariantsNames = group.compatibleVariants.map { it.name }
            val otherCompatibleVariants =
                group.module.variants.map { it.value }.filter { it.name !in groupVariantsNames }
            passiveItem {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    LabelInfo(
                        text = PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.addTo"),
                        textAlign = TextAlign.Center
                    )
                }

            }
            passiveItem {
                Divider(modifier = Modifier.padding(vertical = 4.dp), orientation = Orientation.Horizontal)
            }
            groupVariants.forEach { packageSearchModuleVariant ->
                selectableItem(
                    false,
                    onClick = {
                        addPackageToModule(
                            it = defaultVariant,
                            apiPackage = apiPackage,
                            onlyStable = isOnlyStable,
                            isActionPerforming = isActionPerformingState,
                            dependencyManager = group.dependencyManager,
                            service = service,
                            onActionComplete = { _ -> onActionPerformed() }
                        )
                    }
                ) {
                    Row(
                        modifier = Modifier.defaultMinSize(minWidth = PackageSearchMetrics.Popups.minWidth),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(defaultVariant.name)
                        if (packageSearchModuleVariant.isPrimary) {
                            LabelInfo(
                                text = PackageSearchBundle.message("packagesearch.ui.toolwindow.variant.default.text")
                            )
                        }
                    }
                }
            }
            if (otherCompatibleVariants.isNotEmpty()) {
                passiveItem {
                    Divider(modifier = Modifier.padding(vertical = 4.dp), orientation = Orientation.Horizontal)
                }
            }
            otherCompatibleVariants.sortedBy { it.name }.forEach {
                selectableItem(false, onClick = {
                    addPackageToModule(
                        it = it,
                        apiPackage = apiPackage,
                        onlyStable = isOnlyStable,
                        isActionPerforming = isActionPerformingState,
                        dependencyManager = group.dependencyManager,
                        onActionComplete = { _ -> onActionPerformed() },
                        service = service,
                    )
                }) {
                    Text(modifier = Modifier.defaultMinSize(PackageSearchMetrics.Popups.minWidth), text = it.name)
                }
            }
        }

        is PackageGroup.Remote.FromMultipleModules -> {
            // multi module selection - search is not supported
        }
    }
}
