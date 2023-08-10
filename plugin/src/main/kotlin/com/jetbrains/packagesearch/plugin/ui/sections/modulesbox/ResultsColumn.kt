package org.jetbrains.packagesearch.plugin.ui.sections.modulesbox

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.LocalProjectCoroutineScope
import com.jetbrains.packagesearch.plugin.LocalProjectService
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.latestStableOrNull
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchTableItem
import kotlinx.coroutines.launch
import org.jetbrains.jewel.*
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListScope
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import org.jetbrains.packagesearch.plugin.ui.bridge.openLinkInBrowser
import org.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.LocalPackageRow
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.ModulesHeader
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.RemotePackageRow

interface PackagesGroup {
    val isGroupCollapsed: MutableState<Boolean>
}

class LocalPackagesGroup(
    val header: PackageSearchModuleData,
    val packages: List<PackageSearchTableItem.Installed>,
    override val isGroupCollapsed: MutableState<Boolean> = mutableStateOf(false)
) : PackagesGroup

class RemotePackageGroup(
    val packages: List<PackageSearchTableItem.Remote>,
    val dependencyManager: PackageSearchDependencyManager?,
    override val isGroupCollapsed: MutableState<Boolean> = mutableStateOf(false)
) : PackagesGroup

@Composable
fun ResultsColumn(
    packagesGroupState: List<PackagesGroup>,
    lazyListState: SelectableLazyListState,
    dependencyBrowsingMode: DependenciesBrowsingMode,
    isLoading: Boolean,
    dropDownItemIdOpen: MutableState<Any?>,
    selectedModules: List<PackageSearchModuleData>,
    isActionPerforming: MutableState<Boolean>,
    onPackageClick: (PackageSearchTableItem) -> Unit,
) {
    Box {
        if (packagesGroupState.isEmpty() && !isLoading) {
            NoResultsToShow(dependencyBrowsingMode)
        } else {
            ResultsSelectableLazyColumn(
                packagesGroupState,
                lazyListState,
                onPackageClick,
                dropDownItemIdOpen,
                selectedModules,
                isActionPerforming
            )
        }
        if (isLoading) {
            IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ResultsSelectableLazyColumn(
    results: List<PackagesGroup>,
    selectableState: SelectableLazyListState,
    onPackageClick: (PackageSearchTableItem) -> Unit,
    dropDownItemIdOpen: MutableState<Any?>,
    selectedModules: List<PackageSearchModuleData>,
    isActionPerforming: MutableState<Boolean>
) {
    val projectService = LocalProjectService.current
    val scope = LocalProjectCoroutineScope.current
    SelectableLazyColumn(
        state = selectableState,
    ) {
        when {
            else -> {
                results.forEach { moduleGroup ->
                    stickyHeader(moduleGroup.hashCode()) {
                        when (moduleGroup) {
                            is LocalPackagesGroup -> {
                                val upgradablePackageCount =
                                    moduleGroup.packages.count { it.item.latestStableOrNull != null }
                                ModulesHeader(
                                    moduleName = moduleGroup.header.module.name,
                                    toggleCollapse = {
                                        moduleGroup.isGroupCollapsed.value = !moduleGroup.isGroupCollapsed.value
                                    },
                                    badges = emptyList(),
                                    groupSize = moduleGroup.packages.size,
                                    isGroupExpanded = !moduleGroup.isGroupCollapsed.value,
                                    collectiveActionItemCount = upgradablePackageCount,
                                    availableCollectiveCallback = upgradablePackageCount.takeIf {
                                        it > 0
                                    }?.let {
                                        Pair(
                                            "Upgrade All"
                                        ) {
                                            isActionPerforming.value = true
                                            scope.launch {
                                                moduleGroup.packages.forEach {
                                                    val latest = it.item.remoteInfo?.versions?.latestStable
                                                    if (latest != null) {
                                                        val updateData =
                                                            it.item.getUpdateData(latest.normalized.versionName, null)
                                                        //todo handle concurrent process
                                                        runCatching {
                                                            moduleGroup.header.dependencyManager.updateDependencies(
                                                                projectService,
                                                                listOf(
                                                                    updateData
                                                                ),
                                                                projectService.knownRepositoriesStateFlow.value.values.toList()
                                                            )
                                                        }.onFailure {
                                                            it.printStackTrace()
                                                        }
                                                    }
                                                }
                                            }.invokeOnCompletion {
                                                isActionPerforming.value = false
                                            }

                                        }
                                    },
                                     isActionPerforming = isActionPerforming
                                )
                            }

                            is RemotePackageGroup -> {
                                ModulesHeader(
                                    moduleName = "Search Results",
                                    toggleCollapse = {
                                        moduleGroup.isGroupCollapsed.value = !moduleGroup.isGroupCollapsed.value
                                    },
                                    badges = emptyList(),
                                    groupSize = moduleGroup.packages.size,
                                    isGroupExpanded = !moduleGroup.isGroupCollapsed.value,
                                    availableCollectiveCallback = null,
                                    isActionPerforming = isActionPerforming
                                )
                            }
                        }

                    }
                    if (!moduleGroup.isGroupCollapsed.value) {
                        when (moduleGroup) {
                            is LocalPackagesGroup -> {
                                moduleGroup.packages.forEach {
                                        packageItem(
                                            moduleGroup.header.module.name + "." + it.id,
                                            onItemClick = { onPackageClick(it) }
                                        ) {
                                            LocalPackageRow(
                                                packageSearchDeclaredPackage = it.item,
                                                dropDownItemIdOpen = dropDownItemIdOpen,
                                                selectedModules = selectedModules,
                                                isActionPerforming =  isActionPerforming
                                            )
                                        }
                                    }
                            }

                            is RemotePackageGroup -> {
                                moduleGroup.packages.forEach {
                                    packageItem(
                                        "remote." + it.id,
                                        onItemClick = { onPackageClick(it) }
                                    ) {
                                        RemotePackageRow(
                                            it.item,
                                            dropDownItemIdOpen,
                                            moduleGroup.dependencyManager,
                                            selectedModules,
                                            isActionPerforming =  isActionPerforming
                                        )
                                    }
                                }
                            }

                            else -> {
                                //no handled packageGroup type
                            }
                        }
                    }
                }
            }
        }
    }

}

internal fun SelectableLazyListScope.packageItem(
    itemId: Any,
    onItemClick: () -> Unit,
    itemContent: @Composable () -> Unit
) {
    item(itemId) {
        Box(modifier = Modifier
            .then(
                Modifier.background(
                    when {
                        isSelected && !isFocused -> pickComposeColorFromLaf("Tree.selectionBackground")
                        isSelected && isFocused -> pickComposeColorFromLaf("Tree.selectionInactiveBackground")
                        else -> Color.Unspecified
                    }
                )
            )
            .pointerInput(itemId) {//bug on click consumation
                while (true) {
                    awaitPointerEventScope {
                        awaitFirstDown(false)
                        onItemClick()
                    }
                }
            }
        ) {
            itemContent()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NoResultsToShow(resultType: DependenciesBrowsingMode) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (resultType == DependenciesBrowsingMode.Search) {
            LabelInfo(":( No results found")
            Link(
                resourceLoader = LocalResourceLoader.current,
                text = "Learn more",
                onClick = {
                    runCatching {
                        openLinkInBrowser("https://www.jetbrains.com/help/idea/searching-everywhere.html")
                    }.onFailure {
                        println("Failed to open link in browser: $it")
                    }
                } // todo fix link
            )
        } else {
            LabelInfo("No supported dependencies were found.")
            LabelInfo("Search to add dependencies to the project.")
            Row {
                Icon(
                    painter = painterResource("icons/intui/question.svg", LocalResourceLoader.current),
                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                )
                Link(
                    resourceLoader = LocalResourceLoader.current,
                    text = "Learn more",
                    onClick = {
                        runCatching {
                            openLinkInBrowser("https://www.jetbrains.com/help/idea/package-search.html")
                        }.onFailure {
                            println("Failed to open link in browser: $it")
                        }
                    } // todo fix link
                )
            }
        }
    }
}
