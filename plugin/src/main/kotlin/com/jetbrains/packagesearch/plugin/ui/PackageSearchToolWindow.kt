package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.ui.JBColor
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.model.ToolWindowViewModel
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListViewModel
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IndeterminateHorizontalProgressBar

@Composable
fun PackageSearchToolwindow() {

    val toolWindowViewModel: ToolWindowViewModel = viewModel()
    val packageListViewModel: PackageListViewModel = viewModel()

    val backgroundColor by remember(JewelTheme.isDark) { mutableStateOf(JBColor.PanelBackground.toComposeColor()) }

    val isLoading by toolWindowViewModel.isLoading.collectAsState()
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(top = 2.dp, bottom = 0.dp, start = 2.dp, end = 2.dp),
        ) {
            IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            val message by toolWindowViewModel.loadingMessage.collectAsState()
            message?.let {
                LabelInfo(
                    text = it,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    } else {
        val isInfoPanelOpen by viewModel<ToolWindowViewModel>().isInfoPanelOpen.collectAsState()
        PackageSearchPackagePanel(
            onSelectionModulesSelectionChanged = { packageListViewModel.setSelectedModules(it) },
            isInfoPanelOpen = isInfoPanelOpen,
            onLinkClick = { toolWindowViewModel.openLinkInBrowser(it) },
            onPackageEvent = { packageListViewModel.onPackageListItemEvent(it) },
        )
    }
}

