package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.openLinkInBrowser
import com.jetbrains.packagesearch.plugin.ui.models.InfoBoxDetail
import com.jetbrains.packagesearch.plugin.ui.models.PackageGroup
import com.jetbrains.packagesearch.plugin.ui.models.PackageSearchPackageListItem.Header
import com.jetbrains.packagesearch.plugin.ui.models.PackageSearchPackageListItem.Package
import com.jetbrains.packagesearch.plugin.ui.models.buildPackageSearchPackageItemList
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.PackageGroupHeader
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.PackageRow
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.Link
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.painterResource
import org.jetbrains.jewel.util.appendIf

@Composable
fun PackageSearchPackageList(
    packageGroups: List<PackageGroup>,
    packageGroupState: SnapshotStateList<PackageGroup.Id> = remember { mutableStateListOf() },
    isInfoBoxOpen: Boolean,
    onElementClick: (InfoBoxDetail?) -> Unit,
) {
    val isStableOnly by LocalIsOnlyStableVersions.current.collectAsState()
    val items = remember(packageGroups, packageGroupState.size) {
        buildPackageSearchPackageItemList {
            packageGroups.forEach { group ->
                when (group) {
                    is PackageGroup.Declared -> addFromDeclaredGroup(
                        group = group,
                        isExpanded = group.id !in packageGroupState,
                        isStableOnly = isStableOnly,
                    )

                    is PackageGroup.Remote -> addFromRemoteGroup(
                        group = group,
                        isGroupExpanded = group.id !in packageGroupState,
                        isStableOnly = isStableOnly,
                    )
                }
            }
        }
    }
    SelectableLazyColumn(
        selectionMode = SelectionMode.Single,
        onSelectedIndexesChanged = {
            val index = it.singleOrNull() ?: return@SelectableLazyColumn
            val item = items[index] as Package
            onElementClick(item.infoBoxDetail)
        },
    ) {
        items.forEachIndexed { index, item ->
            when (item) {
                is Header -> stickyHeader(item, "header") {
                    PackageGroupHeader(
                        modifier = Modifier.appendIf(item.groupId in packageGroupState || item.count == 0) {
                            padding(
                                bottom = 1.dp,
                            )
                        },
                        title = item.title,
                        badges = item.badges ?: emptyList(),
                        groupSize = item.count,
                        isGroupExpanded = item.groupId !in packageGroupState,
                        toggleCollapse = {
                            if (item.groupId in packageGroupState) {
                                packageGroupState.remove(item.groupId)
                            } else {
                                packageGroupState.add(item.groupId)
                            }
                        },
                        onBadgesClick = { item.infoBoxDetail?.let(onElementClick) },
                        rightContent = when {
                            item.actionContent != null -> item.actionContent
                            item.compatibleVariantsText != null -> {
                                {
                                    LabelInfo(
                                        modifier = Modifier.clickable { item.infoBoxDetail?.let(onElementClick) },
                                        text = item.compatibleVariantsText,
                                    )
                                }
                            }

                            else -> null
                        },
                    )
                }

                is Package -> item(item, "package") {
                    PackageRow(
                        modifier = Modifier
                            .appendIf(items.getOrNull(index - 1) is Header) {
                                padding(top = 4.dp)
                            }.appendIf(items.getOrNull(index + 1) is Header) {
                                padding(bottom = 4.dp)
                            },
                        isActive = isActive,
                        isSelected = isSelected,
                        isCompact = isInfoBoxOpen,
                        packageIcon = painterResource(item.iconPath, LocalResourceLoader.current),
                        actionPopupId = item.id,
                        packageNameContent = {
                            Text(item.title)
                            item.subtitle?.let { LabelInfo(it) }
                        },
                        editPackageContent = { item.editPackageContent() },
                        popupContent = item.popupContent?.let { { it() } },
                        mainActionContent = item.mainActionContent,
                    )
                }
            }
        }
    }
}

@Composable
fun NoResultsToShow() {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LabelInfo("No supported dependencies were found.")
        LabelInfo("Search to add dependencies to the project.")
        Row {
            Icon(
                painter = painterResource("icons/intui/question.svg", LocalResourceLoader.current),
                modifier = Modifier.size(16.dp).padding(end = 4.dp),
                contentDescription = null,
            )
            Link(
                resourceLoader = LocalResourceLoader.current,
                text = "Learn more",
                onClick = { openLinkInBrowser("https://www.jetbrains.com/help/idea/package-search.html") },
            )
        }
    }
}
