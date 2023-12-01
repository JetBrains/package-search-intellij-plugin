package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent
import com.jetbrains.packagesearch.plugin.ui.panels.packages.PackageSearchCentralPanel
import com.jetbrains.packagesearch.plugin.ui.panels.side.PackageSearchInfoPanel
import com.jetbrains.packagesearch.plugin.ui.panels.tree.PackageSearchModulesTree
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout

@Composable
fun PackageSearchPackagePanel(
    onSelectionModulesSelectionChanged: (Set<PackageSearchModule.Identity>) -> Unit,
    isInfoPanelOpen: Boolean,
    onLinkClick: (String) -> Unit,
    onPackageEvent: (PackageListItemEvent) -> Unit,
) {
    HorizontalSplitLayout(
        first = { PackageSearchModulesTree(it, onSelectionModulesSelectionChanged) },
        second = {
            if (isInfoPanelOpen) {
                HorizontalSplitLayout(
                    modifier = it,
                    initialDividerPosition = 700.dp,
                    first = { PackageSearchCentralPanel(it, onLinkClick) },
                    second = { PackageSearchInfoPanel(it, onLinkClick, onPackageEvent) }
                )
            } else PackageSearchCentralPanel(it, onLinkClick)
        }
    )
}
