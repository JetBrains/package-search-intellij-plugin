package com.jetbrains.packagesearch.plugin.ui.panels.packages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jetbrains.packagesearch.plugin.ui.model.InfoBoxDetail
import com.jetbrains.packagesearch.plugin.ui.model.PackageGroup
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.IndeterminateHorizontalProgressBar

@Composable
fun PackageSearchCentralPanel(
    packagesListState: SelectableLazyListState,
    searchAvailable: Boolean,
    isLoading: Boolean,
    isInfoBoxOpen: Boolean,
    packageGroups: List<PackageGroup>,
    searchQuery: String,
    onElementClick: (InfoBoxDetail?) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
) {

    Column {
        SearchRow(
            searchAvailable = searchAvailable,
            searchQuery = searchQuery,
            searchResultsCount = packageGroups.filterIsInstance<PackageGroup.Remote>()
                .sumOf { it.size },
            onSearchQueryChange = onSearchQueryChange,
        )
        Divider(Orientation.Horizontal)
        Box {
            when {
                packageGroups.isEmpty() && !isLoading -> NoResultsToShow()
                packageGroups.isNotEmpty() -> PackageSearchPackageList(
                    packagesListState = packagesListState,
                    packageGroups = packageGroups,
                    isInfoBoxOpen = isInfoBoxOpen,
                    onElementClick = onElementClick,
                )
            }
            if (isLoading) IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth())
        }

    }
}
