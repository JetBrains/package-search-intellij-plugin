package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.LocalProjectService
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.latestStableOrNull
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchTableItem
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.Link
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.painterResource
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.openLinkInBrowser
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.LocalPackageRow
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.ModulesHeader
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.PackageSearchAction
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.RemotePackageRow

sealed interface PackagesGroup {
    val packages: List<PackageSearchTableItem>
}

fun LocalPackagesGroup.getCollectiveAction(
    projectService: PackageSearchProjectService,
) = packages.count { it.item.latestStableOrNull != null }.takeIf { it > 0 }?.let {
    it to PackageSearchAction(
        "UpgradeAll"
    ) {
        packages.forEach {
            val latest = it.item.remoteInfo?.versions?.latestStable
            if (latest != null) {
                val updateData =
                    it.item.getUpdateData(latest.normalized.versionName, null)
                //todo handle concurrent process
                runCatching {
                    header.dependencyManager.updateDependencies(
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
    }

}

// todo packages should be a map to handle variants in multiplatform
class LocalPackagesGroup(
    val header: PackageSearchModuleData,
    override val packages: List<PackageSearchTableItem.Installed>,
) : PackagesGroup

class RemotePackagesGroup(
    override val packages: List<PackageSearchTableItem.Remote>,
    val dependencyManager: PackageSearchDependencyManager?,
) : PackagesGroup

sealed interface Entry {
    class Header(
        val moduleName: String,
        val moduleSize: Int,
        val availableAction: Pair<Int, PackageSearchAction>? = null,
        val onToggleCollapse: () -> Unit,
    ) : Entry

    class Package(val moduleName: String, val packageItem: PackageSearchTableItem) : Entry

}

@Composable
fun ResultsSelectableLazyColumn(
    results: List<PackagesGroup>,
    dropDownItemIdOpen: MutableState<Any?>,
    selectedModules: List<PackageSearchModuleData>,
    isActionPerforming: MutableState<Boolean>,
    onPackageClick: (PackageSearchTableItem) -> Unit
) {
    val projectService = LocalProjectService.current
    var selectedPackage by remember {
        mutableStateOf<Pair<String, PackageSearchTableItem>?>(null)
    }
    var collapsedGroups by remember {
        mutableStateOf(emptyList<String>())
    }
    val flattenGroups = remember(collapsedGroups, results) {
        buildList {
            results.forEach {
                when (it) {
                    is LocalPackagesGroup -> {
                        add(
                            Entry.Header(
                                it.header.module.name,
                                it.packages.size,
                                it.getCollectiveAction(projectService)
                            )
                            {
                                val name = it.header.module.name
                                collapsedGroups = if (collapsedGroups.contains(name))
                                    collapsedGroups - name
                                else
                                    collapsedGroups + name
                            }
                        )
                        if (!collapsedGroups.contains(it.header.module.name))
                            addAll(it.packages.map { packageItem ->
                                Entry.Package(
                                    it.header.module.name,
                                    packageItem
                                )
                            })
                    }

                    is RemotePackagesGroup -> {
                        add(
                            Entry.Header("Search Results", it.packages.size)
                            {
                                collapsedGroups = if (collapsedGroups.contains("Search Results"))
                                    collapsedGroups - "Search Results"
                                else
                                    collapsedGroups + "Search Results"
                            }
                        )
                        if (!collapsedGroups.contains("Search Results"))
                            addAll(it.packages.map { packageItem -> Entry.Package("remote", packageItem) })
                    }
                }
            }
        }
    }


    SelectableLazyColumn(
        state = rememberSelectableLazyListState(),
        selectionMode = SelectionMode.Single,
        onSelectedIndexesChanged = {
            if (flattenGroups.isNotEmpty() && it.isNotEmpty()) {
                flattenGroups.getOrNull(it.last()).let {
                    if (it is Entry.Package)
                        onPackageClick(it.packageItem)
                }
            }
        }) {
        when {
            else -> {
                flattenGroups.forEach {
                    when (it) {
                        is Entry.Header -> {
                            stickyHeader(it.hashCode()) {
                                ModulesHeader(
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {}
                                        .background(
                                            when {
                                                isSelected && isActive ->
                                                    pickComposeColorFromLaf("Tree.selectionBackground")

                                                isSelected && !isActive ->
                                                    pickComposeColorFromLaf("Tree.selectionInactiveBackground")

                                                else -> Color.Unspecified
                                            }
                                        ),
                                    moduleName = it.moduleName,
                                    toggleCollapse = it.onToggleCollapse,
                                    badges = emptyList(),
                                    groupSize = it.moduleSize,
                                    isGroupExpanded = !collapsedGroups.contains(it.moduleName),
                                    collectiveActionItemCount = it.availableAction?.first ?: 0,
                                    availableCollectiveCallback = it.availableAction?.second,
                                    isActionPerforming = isActionPerforming
                                )
                            }
                        }

                        is Entry.Package -> {
                            when (val packageItem = it.packageItem) {
                                is PackageSearchTableItem.Installed ->
                                    item(
                                        it.moduleName + ":" + packageItem.id,
                                    ) {
                                        LocalPackageRow(
                                            modifier = Modifier
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {}
                                                .background(
                                                    when {
                                                        isSelected && isActive ->
                                                            pickComposeColorFromLaf("Tree.selectionBackground")

                                                        isSelected && !isActive ->
                                                            pickComposeColorFromLaf("Tree.selectionInactiveBackground")

                                                        else -> Color.Unspecified
                                                    }
                                                ),
                                            isActive = isActive,
                                            isSelected = selectedPackage == it.moduleName to it.packageItem,
                                            packageSearchDeclaredPackage = packageItem.item,
                                            dropDownItemIdOpen = dropDownItemIdOpen,
                                            selectedModules = selectedModules,
                                            isActionPerforming = isActionPerforming
                                        )
                                    }


                                is PackageSearchTableItem.Remote ->
                                    item(
                                        "remote:${packageItem.id}"
                                    ) {
                                        RemotePackageRow(
                                            modifier = Modifier
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {}
                                                .background(
                                                    when {
                                                        isSelected && isActive ->
                                                            pickComposeColorFromLaf("Tree.selectionBackground")

                                                        isSelected && !isActive ->
                                                            pickComposeColorFromLaf("Tree.selectionInactiveBackground")

                                                        else -> Color.Unspecified
                                                    }
                                                ),
                                            isActive = isActive,
                                            isSelected = selectedPackage == it.moduleName to it.packageItem,
                                            apiPackage = packageItem.item,
                                            dropDownItemIdOpen = dropDownItemIdOpen,
                                            dependencyManager = selectedModules.firstOrNull()?.dependencyManager,
                                            selectedModules = selectedModules,
                                            isActionPerforming = isActionPerforming
                                        )
                                    }
                            }

                        }
                    }
                }
            }
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
            Link(resourceLoader = LocalResourceLoader.current, text = "Learn more", onClick = {
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
                Link(resourceLoader = LocalResourceLoader.current, text = "Learn more", onClick = {
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
