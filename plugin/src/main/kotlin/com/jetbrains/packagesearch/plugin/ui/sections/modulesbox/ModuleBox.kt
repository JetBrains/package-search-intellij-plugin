package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jetbrains.packagesearch.plugin.ui.model.InfoBoxDetail
import com.jetbrains.packagesearch.plugin.ui.model.PackageGroup
import org.jetbrains.jewel.ui.component.IndeterminateHorizontalProgressBar

@Composable
fun PackageSearchCentralPanel(
    searchAvailable: Boolean,
    isLoading: Boolean,
    isInfoBoxOpen: Boolean,
    packageGroups: List<PackageGroup>,
    searchQuery: String,
    onElementClick: (InfoBoxDetail?) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
) {

    Column {
        AnimatedVisibility(searchAvailable){
            SearchRow(
                searchQuery = searchQuery,
                searchResultsCount = packageGroups.filterIsInstance<PackageGroup.Remote>()
                    .sumOf { it.size },
                onSearchQueryChange = onSearchQueryChange,
            )
        }


        Box {
            when {
                packageGroups.isEmpty() && !isLoading -> NoResultsToShow()
                packageGroups.isNotEmpty() -> PackageSearchPackageList(
                    packageGroups = packageGroups,
                    isInfoBoxOpen = isInfoBoxOpen,
                    onElementClick = onElementClick,
                )
            }
            if (isLoading) IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth())
        }

    }
}
