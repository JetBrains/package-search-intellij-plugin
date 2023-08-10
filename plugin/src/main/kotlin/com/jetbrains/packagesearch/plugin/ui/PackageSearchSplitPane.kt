package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchTableItem
import com.jetbrains.packagesearch.plugin.core.utils.asPackageSearchTableItem
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.tree.Tree
import org.jetbrains.jewel.foundation.tree.rememberTreeState
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest
import org.jetbrains.packagesearch.plugin.services.InfoTabState
import org.jetbrains.packagesearch.plugin.ui.defaultPKGSSplitter
import org.jetbrains.packagesearch.plugin.ui.sections.infobox.InfoBox
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.DependenciesBrowsingMode
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.LocalPackagesGroup
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.ModuleBox
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.RemotePackageGroup
import org.jetbrains.packagesearch.plugin.ui.sections.treebox.TreeBox
import java.awt.Cursor

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun SplitPane(
    detailExpanded: State<InfoTabState>,
    tree: Tree<PackageSearchModuleData>,
    apiClient: PackageSearchApiClient,
    isActionPerforming: MutableState<Boolean>,
) {

    val textSearchState = remember { mutableStateOf("") }
    //packages states
    val dependenciesBrowsingModeStatus = remember { mutableStateOf(DependenciesBrowsingMode.Lookup) }
    //searchParameters
    val searchParams = remember { mutableStateOf(SearchPackagesRequest(emptyList(), "")) }
    var isSearching by remember { mutableStateOf(false) }
    //splitpane States
    val splitPaneState = rememberSplitPaneState(.20f)
    val innerSplitPaneState = rememberSplitPaneState(.80f)
    val splitterColor = LocalScrollbarStyle.current.unhoverColor

    var selectedModules by remember {
        mutableStateOf(emptyList<PackageSearchModuleData>())
    }

    val selectedPackage = remember {
        mutableStateOf<PackageSearchTableItem?>(null)
    }
    //from selected module map the groups
    val lookupPackageGroup = derivedStateOf {
        selectedModules.map {
            it to when (val module = it.module) {
                is PackageSearchModule.Base -> {
                    println("i'm evaluating declaredDependencies")
                    println(module.declaredDependencies.map { it.displayName })
                    module.declaredDependencies.map { it.asPackageSearchTableItem() }
                }

                is PackageSearchModule.WithVariants -> {
                    //todo no this is wrong!! not so easy cowboy!
                    module.variants.flatMap { it.value.declaredDependencies.map { it.asPackageSearchTableItem() } }
                        .toSet().toList()
                }
            }
        }.map {
            LocalPackagesGroup(
                header = it.first,
                packages = it.second
            )
        }
    }
    val searchPackageGroup = remember {
        mutableStateOf<List<RemotePackageGroup>>(emptyList())
    }
    val packagesGroup by derivedStateOf {
        lookupPackageGroup.value +
                if (dependenciesBrowsingModeStatus.value == DependenciesBrowsingMode.Search)
                    searchPackageGroup.value
                else emptyList()
    }//.also { println("fullPackagesGroup size: ${it.value.size}") }

    //infobox
    val infoBoxScrollState = remember(detailExpanded) { ScrollState(0) }
    //this should survive for share popup state for the different composition of 2 or 3 panes in splitpane
    val dropDownItemIdOpen = remember { mutableStateOf<Any?>(null) }
//    val selectableLazyListState = remember(selectedModules) {
//        SelectableLazyListState(LazyListState(), SelectionMode.Single)
//    }
//    LaunchedEffect(selectedPackage.value) {
//        selectedPackage.value?.let {
//            selectableLazyListState.selectSingleKey(it.id, false)
//        }
//    }
    //commons that not need to be persistent
    val lookupSelectableLazyListState =
        remember { SelectableLazyListState(LazyListState(0, 0), SelectionMode.Single) }
    val searchSelectableLazyListState =
        remember { SelectableLazyListState(LazyListState(0, 0), SelectionMode.Single) }

    HorizontalSplitPane(Modifier.fillMaxSize(), splitPaneState) {
        first(100.dp) {
            Column(Modifier.fillMaxHeight().fillMaxWidth()) {
                TreeBox(tree = tree,
                    treeState = rememberTreeState(SelectionMode.Multiple),
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
                        ModuleBox(
                            isLoading = isSearching,
                            packagesGroupsState = packagesGroup,
                            dependenciesBrowsingModeState = dependenciesBrowsingModeStatus,
                            textSearchState = textSearchState,
                            searchParams = searchParams,
                            dropDownItemIdOpen = dropDownItemIdOpen,
                            selectedModules = selectedModules,
                            selectableLazyListState = if (dependenciesBrowsingModeStatus.value == DependenciesBrowsingMode.Lookup) lookupSelectableLazyListState else searchSelectableLazyListState,
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
                ModuleBox(
                    isLoading = isSearching,
                    packagesGroupsState = packagesGroup,
                    dependenciesBrowsingModeState = dependenciesBrowsingModeStatus,
                    textSearchState = textSearchState,
                    searchParams,
                    selectableLazyListState = if (dependenciesBrowsingModeStatus.value == DependenciesBrowsingMode.Lookup) lookupSelectableLazyListState else searchSelectableLazyListState,
                    dropDownItemIdOpen = dropDownItemIdOpen,
                    selectedModules,
                    isActionPerforming
                ) {
                    selectedPackage.value = it
                }

            }
        }
    }
    LaunchedEffect(searchParams.value, dependenciesBrowsingModeStatus) {
        if (dependenciesBrowsingModeStatus.value == DependenciesBrowsingMode.Search) {
            isSearching = true
            runCatching {
                if (selectedModules.size > 1) {
                    // TODO()
                } else {
                    val searchResults = apiClient.searchPackages(searchParams.value)
                    searchResults.let {
                        searchPackageGroup.value = listOf(
                            RemotePackageGroup(
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