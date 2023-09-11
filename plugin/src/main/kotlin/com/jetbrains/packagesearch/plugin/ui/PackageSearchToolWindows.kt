package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.animation.animateColorAsState
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
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import org.jetbrains.jewel.IndeterminateHorizontalProgressBar

@Composable
fun PackageSearchToolwindow(isInfoBoxOpen: Boolean) {
    val backgroundColor = pickComposeColorFromLaf("Tree.background")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(top = 2.dp, bottom = 0.dp, start = 2.dp, end = 2.dp),
    ) {
        val modulesState by LocalProjectService.current.moduleData.collectAsState()
        when (val moduleProvider = modulesState) {
            is ModulesState.Loading -> IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth())
            is ModulesState.Ready -> {
                LocalIsActionPerformingState.current.value = false
                PackageSearchPackagePanel(
                    isInfoBoxOpen = isInfoBoxOpen,
                    tree = moduleProvider.moduleData.asTree(),
                )
            }
        }
    }
}
