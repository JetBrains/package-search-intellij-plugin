package com.jetbrains.packagesearch.plugin.ui.panels.packages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListViewModel
import com.jetbrains.packagesearch.plugin.ui.viewModel
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.IndeterminateHorizontalProgressBar
import org.jetbrains.jewel.ui.component.VerticalScrollbar

@Composable
fun PackageSearchCentralPanel(
    modifier: Modifier,
    onLinkClick: (String) -> Unit,
) = Column(modifier) {
    val viewModel: PackageListViewModel = viewModel()
    val searchQuery by viewModel.searchQueryStateFlow.collectAsState()
    val isOnlineSearchEnabled by viewModel.isOnlineSearchEnabledFlow.collectAsState()
    PackageSearchSearchBar(
        onlineSearchEnabled = isOnlineSearchEnabled,
        searchQuery = searchQuery,
        onSearchQueryChange = { viewModel.setSearchQuery(it) },
        onSearchQueryClear = { viewModel.clearSearchQuery() }
    )
    Divider(Orientation.Horizontal)
    val packagesList by viewModel.packageListItemsFlow.collectAsState()
    Box {
        when {
            packagesList.isEmpty() -> NoResultsToShow(onLinkClick)
            else -> {
                val isCompact by viewModel.isCompactFlow.collectAsState()
                PackageSearchPackageList(
                    packagesList = packagesList,
                    isCompact = isCompact,
                    selectableLazyListState = viewModel.selectableLazyListState,
                    onPackageEvent = viewModel::onPackageListItemEvent,
                )
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState = viewModel.selectableLazyListState.lazyListState),
                    modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                )
            }
        }
        val isLoading by viewModel.isLoadingFlow.collectAsState()
        if (isLoading) IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth())
    }

}

