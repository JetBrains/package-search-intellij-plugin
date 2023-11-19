package com.jetbrains.packagesearch.plugin.ui.panels.packages

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.onClick
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.ui.LearnMoreLink
import com.jetbrains.packagesearch.plugin.ui.LocalGlobalPopupIdState
import com.jetbrains.packagesearch.plugin.ui.LocalInfoBoxPanelOpenState
import com.jetbrains.packagesearch.plugin.ui.LocalIsActionPerformingState
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.PackageSearchMetrics
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.model.InfoBoxDetail
import com.jetbrains.packagesearch.plugin.ui.model.PackageGroup
import com.jetbrains.packagesearch.plugin.ui.model.PackageSearchPackageListItem.Header
import com.jetbrains.packagesearch.plugin.ui.model.PackageSearchPackageListItem.Package
import com.jetbrains.packagesearch.plugin.ui.model.buildPackageSearchPackageItemList
import com.jetbrains.packagesearch.plugin.ui.panels.packages.items.PackageGroupHeader
import com.jetbrains.packagesearch.plugin.ui.panels.packages.items.PackageRow
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun PackageSearchPackageList(
    packagesListState: SelectableLazyListState,
    packageGroups: List<PackageGroup>,
    packageGroupState: SnapshotStateList<PackageGroup.Id> = remember { mutableStateListOf() },
    isInfoBoxOpen: Boolean,
    onElementClick: (InfoBoxDetail?) -> Unit,
) {
    val isStableOnly by LocalIsOnlyStableVersions.current.collectAsState()
    val items = buildPackageSearchPackageItemList {
        packageGroups.forEach { group ->
            when (group) {
                is PackageGroup.Declared -> addFromDeclaredGroup(
                    group = group,
                    isExpanded = group.id !in packageGroupState,
                    isStableOnly = isStableOnly,
                    popupOpenState = LocalGlobalPopupIdState.current,
                    service = LocalPackageSearchService.current,
                    isActionPerformingState = LocalIsActionPerformingState.current
                )

                is PackageGroup.Remote -> addFromRemoteGroup(
                    group = group,
                    isGroupExpanded = group.id !in packageGroupState,
                    service = LocalPackageSearchService.current,
                    isActionPerformingState = LocalIsActionPerformingState.current,
                    isOnlyStable = LocalIsOnlyStableVersions.current.value
                )
            }
        }
    }
    var infoBoxOpenState by LocalInfoBoxPanelOpenState.current
    SelectableLazyColumn(
        selectionMode = SelectionMode.Single,
        state = packagesListState,
        onSelectedIndexesChanged = {
            val index = it.singleOrNull() ?: return@SelectableLazyColumn
            val item = items[index] as Package
            onElementClick(item.infoBoxDetail)
        },
    ) {
        items.forEachIndexed { index, item ->
            when (item) {
                is Header -> stickyHeader("$index.${item.uniqueId()}", contentType = "header") {

                    PackageGroupHeader(
                        modifier = Modifier.padding(PackageSearchMetrics.PackagesList.header),
                        title = item.title,
                        badges = item.badges ?: emptyList(),
                        groupSize = item.count,
                        isGroupExpanded = item.groupId !in packageGroupState,
                        toggleCollapse = {
                            if (item.count == 0) return@PackageGroupHeader
                            if (item.groupId in packageGroupState) {
                                packageGroupState.remove(item.groupId)
                            } else {
                                packageGroupState.add(item.groupId)
                            }
                        },
                        onBadgesClick = {
                            item.infoBoxDetail?.let(onElementClick)
                            if (!isInfoBoxOpen) infoBoxOpenState = true
                        },
                        rightContent = when {
                            item.actionContent != null -> item.actionContent
                            item.compatibleVariantsText != null -> {
                                {
                                    LabelInfo(
                                        modifier = Modifier,
//                                            .clickable(
//                                                indication = null,
//                                                interactionSource = remember { MutableInteractionSource() }) {
//                                                item.infoBoxDetail?.let(onElementClick)
//                                                if (!isInfoBoxOpen) infoBoxOpenState = true
//                                            }
//                                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                                        text = item.compatibleVariantsText,
                                    )
                                }
                            }

                            else -> null
                        },
                    )
                }

                is Package -> item(item.uniqueId(), contentType = "package") {
                    val itemPaddings by derivedStateOf {
                        PackageSearchMetrics.PackagesList.Package.paddingFor(
                            isFirstItem = items.getOrNull(index - 1) is Header,
                            isLastItem = items.getOrNull(index + 1) !is Package
                        )
                    }
                    PackageRow(
                        modifier = Modifier
                            .padding(itemPaddings)
                            .onClick(
                                interactionSource = remember { MutableInteractionSource() },
                                onDoubleClick = {
                                    if (!isInfoBoxOpen) {
                                        infoBoxOpenState = true
                                    }
                                },
                                onClick = { }
                            ),
                        isActive = isActive,
                        isSelected = isSelected,
                        isCompact = isInfoBoxOpen,
                        icon = if (JewelTheme.isDark) item.icon.darkIconPath else item.icon.lightIconPath,
                        actionPopupId = item.groupId + "." + item.id,
                        packageNameContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(item.title, maxLines = 1)
                                item.subtitle?.let { LabelInfo(it, Modifier.weight(1f), maxLines = 1) }
                            }
                        },
                        editPackageContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                item.editPackageContent()
                            }
                        },
                        popupContent = item.popupContent,
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
        LabelInfo(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.noSupportedDependencyFound.l1"))
        LabelInfo(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.noSupportedDependencyFound.l2"))
        LearnMoreLink()
    }
}
