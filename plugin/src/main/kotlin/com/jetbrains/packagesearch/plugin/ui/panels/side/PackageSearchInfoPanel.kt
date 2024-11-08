package com.jetbrains.packagesearch.plugin.ui.panels.side

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.ui.PackageSearchMetrics
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.model.infopanel.InfoPanelContent
import com.jetbrains.packagesearch.plugin.ui.model.infopanel.InfoPanelViewModel
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent
import com.jetbrains.packagesearch.plugin.ui.viewModel
import org.jetbrains.jewel.ui.component.SimpleTabContent
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.VerticalScrollbar

@Composable
fun PackageSearchInfoPanel(
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit,
    onPackageEvent: (PackageListItemEvent) -> Unit,
) = Box(modifier) {
    val viewModel = viewModel<InfoPanelViewModel>()
    val tabs by viewModel.tabs.collectAsState()
    val activeTabTitle by viewModel.activeTabTitleFlow.collectAsState()
    // if you use `by derivedStateOf`, the then will fail
    val activeTab = derivedStateOf { tabs.firstOrNull { it.tabTitleData.tabTitle == activeTabTitle } }.value
    when {
        tabs.isEmpty() || activeTab == null -> NoTabsAvailable()
        else -> Column(modifier = Modifier.fillMaxSize()) {
            TabStrip(
                modifier = Modifier.fillMaxWidth(),
                tabs = tabs.map { infoPanelContent ->
                    TabData.Default(
                        selected = activeTabTitle == infoPanelContent.tabTitleData.tabTitle,
                        closable = false,
                        content = { tabState ->
                            SimpleTabContent(infoPanelContent.tabTitleData.tabTitle, tabState)
                        },
                        onClick = { viewModel.setActiveTabTitle(infoPanelContent.tabTitleData.tabTitle) },
                    )
                }
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .padding(end = PackageSearchMetrics.scrollbarWidth)
                        .verticalScroll(viewModel.scrollState)
                ) {
                    when (activeTab) {
                        is InfoPanelContent.PackageInfo -> {
                            PackageOverviewTab(
                                onLinkClick = onLinkClick,
                                onPackageEvent = onPackageEvent,
                                content = activeTab
                            )
                        }

                        is InfoPanelContent.Attributes.FromVariantHeader -> {
                            HeaderAttributesTab(content = activeTab, scrollState = viewModel.scrollState)

                        }

                        is InfoPanelContent.Attributes.FromSearchHeader -> {
                            HeaderAttributesTab(content = activeTab, scrollState = viewModel.scrollState)
                        }

                        is InfoPanelContent.Attributes.FromPackage -> HeaderAttributesTab(
                            content = activeTab,
                            scrollState = viewModel.scrollState
                        )
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                    scrollState = viewModel.scrollState,
                )
            }
        }
    }
}

@Composable
private fun NoTabsAvailable() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LabelInfo(
            PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.noItemSelected"),
            textAlign = TextAlign.Center
        )
    }
}
