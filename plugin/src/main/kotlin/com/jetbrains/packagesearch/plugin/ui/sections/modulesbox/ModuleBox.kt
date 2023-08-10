package org.jetbrains.packagesearch.plugin.ui.sections.modulesbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchTableItem
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest

enum class DependenciesBrowsingMode {
    Search, // the result content in showing the search result from the api
    Lookup // the result content in showing the dependencies of the selected module
}

@Composable
fun ModuleBox(
    isLoading: Boolean = false,
    packagesGroupsState: List<PackagesGroup>,
    dependenciesBrowsingModeState: MutableState<DependenciesBrowsingMode>,
    textSearchState: MutableState<String>,
    searchParams: MutableState<SearchPackagesRequest>,
    selectableLazyListState: SelectableLazyListState,
    dropDownItemIdOpen: MutableState<Any?>,
    selectedModules: List<PackageSearchModuleData>,
    isActionPerforming: MutableState<Boolean>,
    onPackageClick: (PackageSearchTableItem) -> Unit = {}
) {
    Box {
        Column {
            SearchRow(
                textSearchState = textSearchState,
                searchResultsCount =
                    packagesGroupsState.filterIsInstance<RemotePackageGroup>().sumOf { it.packages.size },
                dependenciesBrowsingModeStatus = dependenciesBrowsingModeState
            ) {
                searchParams.value = searchParams.value.copy(searchQuery = it)
            }
            ResultsColumn(
                packagesGroupState = packagesGroupsState.let {
                    if (dependenciesBrowsingModeState.value == DependenciesBrowsingMode.Search) {
                        it.map {
                            if (it is LocalPackagesGroup) {
                                LocalPackagesGroup(
                                    it.header,
                                    it.packages.filter {
                                        it.id.contains(textSearchState.value) ||
                                                it.displayName.contains(textSearchState.value)
                                    }
                                )
                            } else it
                        }
                    } else it
                },
                lazyListState = selectableLazyListState,
                dependencyBrowsingMode = dependenciesBrowsingModeState.value,
                isLoading = isLoading,
                dropDownItemIdOpen = dropDownItemIdOpen,
                selectedModules = selectedModules,
                isActionPerforming = isActionPerforming
            ) { onPackageClick(it) }
        }
    }
}

