package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.services.ModulesState
import com.jetbrains.packagesearch.plugin.ui.bridge.asTree
import org.jetbrains.jewel.IndeterminateHorizontalProgressBar
import org.jetbrains.jewel.styling.LocalMenuStyle


@Composable
fun PackageSearchToolwindow(isInfoBoxOpen: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // TODO replace with generic surface color from theme
            .background(LocalMenuStyle.current.colors.background)
            .padding(top = 2.dp, bottom = 0.dp, start = 2.dp, end = 2.dp)
    ) {
        val modulesState by LocalProjectService.current.modules.collectAsState()
        when (val moduleProvider = modulesState) {
            is ModulesState.Loading -> IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth())
            is ModulesState.Ready -> {
                LocalIsActionPerformingState.current.value = false
                PackageSearchPackagePanel(
                    isInfoBoxOpen = isInfoBoxOpen,
                    tree = moduleProvider.modules.asTree()
                )
            }
        }
    }
}