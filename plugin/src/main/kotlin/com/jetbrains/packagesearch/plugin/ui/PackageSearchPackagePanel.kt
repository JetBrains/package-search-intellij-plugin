package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.ui.JBColor
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.ui.bridge.defaultPKGSSplitter
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent
import com.jetbrains.packagesearch.plugin.ui.panels.packages.PackageSearchCentralPanel
import com.jetbrains.packagesearch.plugin.ui.panels.side.PackageSearchInfoPanel
import com.jetbrains.packagesearch.plugin.ui.panels.tree.PackageSearchModulesTree
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.theme.JewelTheme

@Composable
fun PackageSearchPackagePanel(
    onSelectionModulesSelectionChanged: (Set<PackageSearchModule.Identity>) -> Unit,
    isInfoPanelOpen: Boolean,
    onLinkClick: (String) -> Unit,
    onPackageEvent: (PackageListItemEvent) -> Unit,
) {
    val splitPaneState = rememberSplitPaneState(PackageSearchMetrics.Splitpane.firstSplitterPositionPercentage )
    val innerSplitPaneState = rememberSplitPaneState(PackageSearchMetrics.Splitpane.secondSplittePositionPercentage)
    val splitterColor by remember(JewelTheme.isDark) { mutableStateOf(JBColor.border().toComposeColor()) }

    HorizontalSplitPane(Modifier.fillMaxSize(), splitPaneState) {
        first {
            PackageSearchModulesTree(Modifier, onSelectionModulesSelectionChanged)
        }
        defaultPKGSSplitter(splitterColor)
        second {
            if (isInfoPanelOpen) {
                HorizontalSplitPane(Modifier.fillMaxSize(), innerSplitPaneState) {
                    first {
                        PackageSearchCentralPanel(onLinkClick = onLinkClick)
                    }
                    defaultPKGSSplitter(splitterColor)
                    second(300.dp) {
                        PackageSearchInfoPanel(onLinkClick = onLinkClick, onPackageEvent = onPackageEvent)
                    }
                }

            } else PackageSearchCentralPanel(onLinkClick = onLinkClick)
        }
    }
}

