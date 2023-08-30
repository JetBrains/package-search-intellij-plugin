package org.jetbrains.packagesearch.plugin.ui.sections.modulesbox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchTableItem
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.NoResultsToShow
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.PackagesGroup
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.RemotePackagesGroup
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.ResultsSelectableLazyColumn
import org.jetbrains.jewel.IndeterminateHorizontalProgressBar
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest

enum class DependenciesBrowsingMode {
    Search, // the result content in showing the search result from the api
    Lookup // the result content in showing the dependencies of the selected module
}

@Composable
fun PackageSearchCentralPanel(
    isLoading: Boolean = false,
    packagesGroups: List<PackagesGroup>,
    dependenciesBrowsingModeState: MutableState<DependenciesBrowsingMode>,
    textSearchState: MutableState<String>,
    searchParams: MutableState<SearchPackagesRequest>,
    dropDownItemIdOpen: MutableState<Any?>,
    selectedModules: List<PackageSearchModuleData>,
    isActionPerforming: MutableState<Boolean>,
    onPackageClick: (PackageSearchTableItem) -> Unit = {}
) {
    Column {
        SearchRow(
            textSearchState = textSearchState,
            searchResultsCount =
            packagesGroups.filterIsInstance<RemotePackagesGroup>().sumOf { it.packages.size },
            dependenciesBrowsingModeStatus = dependenciesBrowsingModeState
        ) {
            searchParams.value = searchParams.value.copy(searchQuery = it)
        }

        if (packagesGroups.isEmpty() && !isLoading) {
            NoResultsToShow(dependenciesBrowsingModeState.value)
        } else {
            ResultsSelectableLazyColumn(
                packagesGroups,
                dropDownItemIdOpen,
                selectedModules,
                isActionPerforming,
                onPackageClick
            )
        }
        if (isLoading) {
            IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth())
        }

    }
}


