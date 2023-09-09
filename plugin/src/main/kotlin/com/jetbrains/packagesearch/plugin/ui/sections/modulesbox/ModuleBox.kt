package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jetbrains.packagesearch.plugin.ui.models.InfoBoxDetail
import com.jetbrains.packagesearch.plugin.ui.models.PackageGroup
import org.jetbrains.jewel.IndeterminateHorizontalProgressBar
import org.jetbrains.jewel.IntelliJTheme

@Composable
fun PackageSearchCentralPanel(
    isLoading: Boolean,
    isInfoBoxOpen: Boolean,
    packageGroups: List<PackageGroup>,
    searchQuery: String,
    onElementClick: (InfoBoxDetail?) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
) {
    Column {
        SearchRow(
            searchQuery = searchQuery,
            searchResultsCount = packageGroups.filterIsInstance<PackageGroup.Remote>()
                .sumOf { it.size },
            onSearchQueryChange = onSearchQueryChange,
        )

        if (isLoading) {
            IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth())
        } else {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .height(IntelliJTheme.horizontalProgressBarStyle.metrics.minHeight),
            )
        }

        when {
            packageGroups.isEmpty() && !isLoading -> NoResultsToShow()
            packageGroups.isNotEmpty() -> PackageSearchPackageList(
                packageGroups = packageGroups,
                isInfoBoxOpen = isInfoBoxOpen,
                onElementClick = onElementClick,
            )
        }
    }
}
