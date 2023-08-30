package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchTableItem
import com.jetbrains.packagesearch.plugin.core.utils.asPackageSearchTableItem
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.LocalPackagesGroup
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.RemotePackagesGroup
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.foundation.tree.Tree
import org.jetbrains.jewel.foundation.tree.rememberTreeState
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest
import org.jetbrains.packagesearch.plugin.services.InfoTabState
import org.jetbrains.packagesearch.plugin.ui.defaultPKGSSplitter
import org.jetbrains.packagesearch.plugin.ui.sections.infobox.InfoBox
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.DependenciesBrowsingMode
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.PackageSearchCentralPanel
import org.jetbrains.packagesearch.plugin.ui.sections.treebox.PackageSearchModulesTree
import java.awt.Cursor

@Composable
fun PackageSearchPackagePanel(
    detailExpanded: State<InfoTabState>,
    tree: Tree<PackageSearchModuleData>,
    apiClient: PackageSearchApiClient,
    isActionPerforming: MutableState<Boolean>,
) {
    // search Handling
    val textSearchState = remember { mutableStateOf("") }
    val dependenciesBrowsingModeStatus = remember { mutableStateOf(DependenciesBrowsingMode.Lookup) }
    val searchParams = remember { mutableStateOf(SearchPackagesRequest(emptyList(), "")) }
    var isSearching by remember { mutableStateOf(false) }

    // splitpane states
    val splitPaneState = rememberSplitPaneState(.20f)
    val innerSplitPaneState = rememberSplitPaneState(.80f)
    val splitterColor = LocalScrollbarStyle.current.unhoverColor
    val infoBoxScrollState = remember(detailExpanded.value) { ScrollState(0) }


    // modules and packages handling
    var selectedModules by remember {
        mutableStateOf(emptyList<PackageSearchModuleData>())
    }

    val selectedPackage = remember {
        mutableStateOf<PackageSearchTableItem?>(null)
    }

    var searchPackageGroup by remember {
        mutableStateOf<List<RemotePackagesGroup>>(emptyList())
    }

    val packagesGroups =
        remember(
            selectedModules,
            dependenciesBrowsingModeStatus.value,// pass just the value not the state
            searchPackageGroup,
            tree
        ) {
            selectedModules.distinct().map { //todo check without distinct
                it to when (val module = it.module) {
                    is PackageSearchModule.Base -> {
                        module.declaredDependencies.map { it.asPackageSearchTableItem() }
                    }

                    is PackageSearchModule.WithVariants -> {
                        // todo should consider the other variants
                        module.mainVariant.declaredDependencies.map { it.asPackageSearchTableItem() }
                    }
                }.filter {
                    if (dependenciesBrowsingModeStatus.value != DependenciesBrowsingMode.Lookup)
                        it.id.contains(textSearchState.value) ||
                                it.displayName.contains(textSearchState.value)
                    else
                        true
                }
            }.map {
                LocalPackagesGroup(
                    header = it.first,
                    packages = it.second
                )
            }.let { localPackages ->
                localPackages + if (dependenciesBrowsingModeStatus.value == DependenciesBrowsingMode.Search) {
                    searchPackageGroup.map {
                        RemotePackagesGroup(
                            it.packages.filter {
                                it.id !in localPackages.flatMap { it.packages }.map { it.id }
                            },
                            it.dependencyManager
                        )
                    }
                } else emptyList()
            }
        }
    val treeState = rememberTreeState()
    val dropDownItemIdOpen = remember { mutableStateOf<Any?>(null) }

    HorizontalSplitPane(Modifier.fillMaxSize(), splitPaneState) {
        first(100.dp) {
            Column(Modifier.fillMaxHeight().fillMaxWidth()) {
                PackageSearchModulesTree(
                    tree = tree,
                    treeState = treeState,
                    onSelectionChange = {
                        selectedModules = it.map { it.data }
                    }
                )
            }
        }
        val expanded = detailExpanded.value is InfoTabState.Open
        defaultPKGSSplitter(splitterColor, cursor = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
        second(500.dp) {
            if (expanded) {
                HorizontalSplitPane(Modifier.fillMaxSize(), innerSplitPaneState) {
                    first(100.dp) {
                        PackageSearchCentralPanel(
                            isLoading = isSearching,
                            packagesGroups = packagesGroups,
                            dependenciesBrowsingModeState = dependenciesBrowsingModeStatus,//todo transform in a lambda
                            textSearchState = textSearchState,
                            searchParams = searchParams,
                            dropDownItemIdOpen = dropDownItemIdOpen,
                            selectedModules = selectedModules,
                            isActionPerforming = isActionPerforming
                        ) {
                            selectedPackage.value = it
                        }
                    }
                    defaultPKGSSplitter(splitterColor, cursor = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                    second(160.dp) {
                        Box {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(infoBoxScrollState)
                            ) {
                                InfoBox(selectedPackage.value, selectedModules)
                            }
                            Box(modifier = Modifier.matchParentSize()) {
                                VerticalScrollbar(
                                    rememberScrollbarAdapter(infoBoxScrollState),
                                    modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }
                }
            } else {
                PackageSearchCentralPanel(
                    isLoading = isSearching,
                    packagesGroups = packagesGroups,
                    dependenciesBrowsingModeState = dependenciesBrowsingModeStatus,
                    textSearchState = textSearchState,
                    searchParams = searchParams,
                    dropDownItemIdOpen = dropDownItemIdOpen,
                    selectedModules = selectedModules,
                    isActionPerforming = isActionPerforming
                ) {
                    selectedPackage.value = it
                }

            }
        }
    }
    LaunchedEffect(searchParams.value, dependenciesBrowsingModeStatus, selectedModules) {
        if (dependenciesBrowsingModeStatus.value == DependenciesBrowsingMode.Search) {
            isSearching = true
            runCatching {
                if (selectedModules.size > 1) {
                    // TODO()
                } else {
                    val searchResults = apiClient.searchPackages(searchParams.value)
                    searchResults.let {
                        searchPackageGroup = listOf(
                            RemotePackagesGroup(
                                searchResults.map { it.asPackageSearchTableItem() },
                                selectedModules.firstOrNull()?.dependencyManager
                            )
                        )
                    }
                }
            }.onFailure {
                it.printStackTrace()
            }
            isSearching = false
        }
    }
}