package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.ui.JBColor
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.ui.model.InfoBoxDetail
import com.jetbrains.packagesearch.plugin.ui.model.SearchData
import com.jetbrains.packagesearch.plugin.ui.model.buildDeclaredPackageGroups
import com.jetbrains.packagesearch.plugin.ui.model.buildRemotePackageGroups
import com.jetbrains.packagesearch.plugin.ui.model.buildSearchData
import com.jetbrains.packagesearch.plugin.ui.model.plus
import com.jetbrains.packagesearch.plugin.ui.sections.infobox.PackageSearchInfoBox
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.PackageSearchCentralPanel
import com.jetbrains.packagesearch.plugin.ui.sections.treebox.PackageSearchModulesTree
import com.jetbrains.packagesearch.plugin.utils.logInfo
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApi
import org.jetbrains.packagesearch.api.v3.http.SearchPackagesRequest

@Composable
fun PackageSearchPackagePanel(
    isInfoBoxOpen: Boolean,
    state: TreeState,
    tree: Tree<PackageSearchModuleData>,
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val splitPaneState = rememberSplitPaneState(.20f)
    val innerSplitPaneState = rememberSplitPaneState(.80f)
    val splitterColor by remember(JewelTheme.isDark) { mutableStateOf(JBColor.border().toComposeColor()) }

    val infoBoxScrollState = rememberScrollState()

    var infoBoxDetail by remember { mutableStateOf<InfoBoxDetail?>(null) }


    var selectedModules by remember { mutableStateOf<List<PackageSearchModuleData>>(emptyList()) }
    var searchResults by remember { mutableStateOf<SearchData.Results>(SearchData.Results.Empty) }
    val selectedModulesIdentity by derivedStateOf {
        selectedModules.map { it.module.identity }
    }
    remember(selectedModulesIdentity) { searchResults = SearchData.Results.Empty }
    val declaredPackageGroups by derivedStateOf {
        buildDeclaredPackageGroups(searchQuery) {
            setLocal(selectedModules)
        }
    }

    val remotePackageGroup by derivedStateOf {
        buildRemotePackageGroups(searchQuery) {
            setSearchResults(searchResults, selectedModules)
        }
    }

    val packageGroups by derivedStateOf {
        declaredPackageGroups + remotePackageGroup
    }

    // we need to refresh side panel on new tree emission cause something might have changed
    remember(selectedModules) {
        val infoBoxValue = infoBoxDetail as? InfoBoxDetail.Package.DeclaredPackage
        if (infoBoxValue != null) {
            infoBoxDetail = declaredPackageGroups.value
                .firstOrNull() { it.module.identity == infoBoxValue.module.identity }
                ?.filteredDependencies?.firstOrNull() { it.id == infoBoxValue.declaredDependency.id }
                ?.let { InfoBoxDetail.Package.DeclaredPackage(it, infoBoxValue.module, infoBoxValue.dependencyManager) }
        }

    }

    @Composable
    fun PackageSearchCentralPanel() {
        val searchAvailable by derivedStateOf {
            selectedModules.size == 1
        }
        if (selectedModules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Select one or more modules on the left to show declared dependencies")
            }
        } else {
            PackageSearchCentralPanel(
                searchAvailable = searchAvailable,
                isLoading = isSearching,
                isInfoBoxOpen = isInfoBoxOpen,
                packageGroups = packageGroups,
                searchQuery = searchQuery,
                onElementClick = { infoBoxDetail = it },
                onSearchQueryChange = { searchQuery = it },
            )
        }
    }

    HorizontalSplitPane(Modifier.fillMaxSize(), splitPaneState) {
        first(100.dp) {
            Column(Modifier.fillMaxSize()) {
                PackageSearchModulesTree(tree, state) { selectedModules = it }
            }
        }
        defaultPKGSSplitter(splitterColor)
        second(500.dp) {
            if (isInfoBoxOpen) {
                HorizontalSplitPane(Modifier.fillMaxSize(), innerSplitPaneState) {
                    first(minSize = 100.dp) {
                        PackageSearchCentralPanel()
                    }
                    defaultPKGSSplitter(splitterColor)
                    second(160.dp) {
                        Box {
                            Column(
                                modifier = Modifier.verticalScroll(infoBoxScrollState),
                            ) {
                                PackageSearchInfoBox(infoBoxDetail, selectedModules)
                            }
                            Box(modifier = Modifier.matchParentSize()) {
                                VerticalScrollbar(
                                    rememberScrollbarAdapter(infoBoxScrollState),
                                    modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                                )
                            }
                        }
                    }
                }
            } else {
                PackageSearchCentralPanel()
            }
        }
    }

    val json = LocalJson.current
    val apiClient = LocalPackageSearchApiClient.current

    LaunchedEffect(selectedModulesIdentity, searchQuery) {
        if (searchQuery.isBlank() || selectedModules.size > 1) {
            searchResults = SearchData.Results.Empty
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(250.milliseconds)
        searchResults = when (val searchData = buildSearchData(selectedModules, searchQuery)) {
            SearchData.Empty -> SearchData.Results.Empty
            is SearchData.SingleBaseModule ->
                searchData.withResults(apiClient.trySearchPackages(json, searchData.searchParameters))

            is SearchData.MultipleModules ->
                searchData.withResults(apiClient.trySearchPackages(json, searchData.searchParameters))

            is SearchData.SingleModuleWithVariants -> {
                val searchMap = searchData
                    .searches
                    .map { it.searchParameters }
                    .distinct()
                    .associateWith { async { apiClient.trySearchPackages(json, it) } }

                searchData.withResults(
                    results = searchData.searches
                        .map { it.withResults(searchMap.getValue(it.searchParameters).await()) }
                )
            }
        }
        isSearching = false
    }
}

private suspend fun PackageSearchApi.trySearchPackages(json: Json, request: SearchPackagesRequest) =
    runCatching { searchPackages(request) }
        .onFailure {
            logInfo(throwable = it) {
                """
Error while searching. Request:
|----------------
|${json.encodeToString(request)}
|----------------
                """.trimIndent()
            }
        }
        .getOrDefault(emptyList())
