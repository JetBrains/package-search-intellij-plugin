package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.model.NoModulesFoundViewMode
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link



@Composable
fun NoModulesFound(
    onLinkClicked: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LabelInfo("No supported modules were found.")
        val viewModel = viewModel<NoModulesFoundViewMode>()
        val hasExternalProjects by viewModel.hasExternalProjects.collectAsState()
        if (hasExternalProjects) {
            Row {
                LabelInfo("Try ")
                val isEnabled by viewModel.isRefreshing.collectAsState()
                Link(
                    enabled = isEnabled,
                    text = "refreshing",
                    onClick = { viewModel.refreshExternalProjects() },
                )
                LabelInfo(" external projects")
            }
        }
        LearnMoreLink(onLinkClicked)
    }
}

@Composable
fun LearnMoreLink(onLinkClick: (String) -> Unit) {
    Row {
        Icon(
            resource = "icons/intui/question.svg",
            modifier = Modifier.size(16.dp).padding(end = 4.dp),
            contentDescription = null,
            tint = JewelTheme.globalColors.infoContent,
            iconClass = IconProvider::class.java
        )
        Link(
            text = "Learn more",
            onClick = { onLinkClick("https://www.jetbrains.com/help/idea/package-search.html") },
        )
    }
}