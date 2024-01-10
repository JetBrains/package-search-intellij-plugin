package com.jetbrains.packagesearch.plugin.ui.panels.tree

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.jetbrains.packagesearch.plugin.PackageSearchBundle.message
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.ui.PackageSearchMetrics
import com.jetbrains.packagesearch.plugin.ui.model.tree.TreeItemModel
import com.jetbrains.packagesearch.plugin.ui.model.tree.TreeViewModel
import com.jetbrains.packagesearch.plugin.ui.viewModel
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.LazyTree
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticalScrollbar

@Composable
fun PackageSearchModulesTree(
    modifier: Modifier,
    onSelectionChanged: (Set<PackageSearchModule.Identity>) -> Unit,
) = Column(modifier) {
    val viewModel: TreeViewModel = viewModel()
    val knownNodes = remember { mutableSetOf<PackageSearchModule.Identity>() }

    val tree by viewModel.tree.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    TreeActionToolbar(
        isOnline = isOnline,
        onExpandAll = viewModel::expandAll,
        onCollapseAll = {
            val rootIds = tree.roots.map { it.id }.toSet()
            viewModel.treeState.selectedKeys = viewModel.treeState.selectedKeys intersect rootIds
            viewModel.collapseAll()
        },
    )
    Divider(Orientation.Horizontal)
    remember(tree, viewModel.treeState.openNodes) {
        val newNodes = tree.walkBreadthFirst()
            .filterIsInstance<Tree.Element.Node<TreeItemModel>>()
            .map { it.data.id }
            .toSet() - knownNodes

        knownNodes += newNodes

        viewModel.treeState.openNodes += newNodes

        if (viewModel.treeState.selectedKeys.isEmpty()) {
            viewModel.treeState.selectedKeys = tree.walkBreadthFirst()
                .take(1)
                .map { it.data.id }
                .toSet()
        }
    }
    Box {
        LazyTree(
            modifier = Modifier.padding(top = 4.dp, end = PackageSearchMetrics.scrollbarWidth),
            tree = tree,
            treeState = viewModel.treeState,
            onSelectionChange = {
                onSelectionChanged(
                    it.map { it.id }
                        .filterIsInstance<PackageSearchModule.Identity>()
                        .toSet()
                )
            },
        ) { item ->
            TreeItem(item)
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState = viewModel.lazyListState),
            modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
        )
    }


}

@Composable
private fun TreeActionToolbar(
    isOnline: Boolean,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
) {
    Row(
        modifier = Modifier.height(PackageSearchMetrics.treeActionsHeight)
            .padding(vertical = 5.dp, horizontal = 7.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            Tooltip(
                tooltip = { Text(message("packagesearch.ui.tree.tooltips.expand")) },
                content = {
                    IconButton(onClick = onExpandAll) {
                        Icon(
                            modifier = Modifier.padding(5.dp),
                            resource = "actions/expandall.svg",
                            iconClass = AllIcons::class.java,
                            contentDescription = "Collapse PKGS Tree"
                        )
                    }
                }
            )
            Tooltip(
                tooltip = { Text(message("packagesearch.ui.tree.tooltips.collapse")) },
                content = {
                    IconButton(
                        onClick = onCollapseAll,
                    )
                    {
                        Icon(
                            modifier = Modifier.padding(5.dp),
                            resource = "actions/collapseall.svg",
                            iconClass = AllIcons::class.java,
                            contentDescription = "Collapse PKGS Tree"
                        )
                    }
                }
            )
        }

        Crossfade(isOnline) {
            if (!it) {
                Tooltip(
                    tooltip = { Text(message("packagesearch.ui.tree.tooltips.offline")) },
                    content = {
                        Icon(
                            modifier = Modifier.padding(5.dp),
                            resource = "icons/intui/toggleOfflineMode.svg",
                            iconClass = IconProvider::class.java,
                            contentDescription = "Package FromSearch is offline."
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun TreeItem(element: Tree.Element<TreeItemModel>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (element !is Tree.Element.Node) {
                Spacer(modifier = Modifier.width(12.dp))
            }
            Icon(
                modifier = Modifier.size(16.dp),
                resource = if (JewelTheme.isDark) element.data.icon.darkIconPath else element.data.icon.lightIconPath,
                contentDescription = null,
                iconClass = IconProvider::class.java
            )
            Text(
                text = element.data.text,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (element.data.hasUpdates) {
            Icon(
                modifier = Modifier.padding(end = 12.dp),
                resource = "icons/intui/upgradableMark.svg",
                iconClass = IconProvider::class.java,
                contentDescription = ""
            )
        }
    }
}
//
//@Preview
//@Composable
//private fun TreeItemPreview() {
//    val items = listOf(
//        TreeItemModel(
//            id = PackageSearchModule.Identity("a", ":"),
//            text = "JetBrains",
//            hasUpdates = true,
//            icon = IconProvider.Icon("icons/npm.svg"),
//        ),
//        TreeItemModel(
//            id = PackageSearchModule.Identity("a", ":b"),
//            text = "Kotlin",
//            hasUpdates = false,
//            icon = IconProvider.Icon("icons/maven.svg"),
//        ),
//        TreeItemModel(
//            id = PackageSearchModule.Identity("a", ":c"),
//            text = "Ktor",
//            hasUpdates = false,
//            icon = IconProvider.Icon("icons/cocoapods.svg.svg"),
//        ),
//        TreeItemModel(
//            id = PackageSearchModule.Identity("a", ":c:d"),
//            text = "Compose",
//            hasUpdates = true,
//            icon = IconProvider.Icon("icons/npm.svg"),
//        ),
//    )
//    val tree = buildTree {
//        addLeaf(items[0], items[0].id)
//        addNode(items[1], items[1].id) {
//            addLeaf(items[2], items[2].id)
//        }
//        addLeaf(items[3], items[3].id)
//    }
//    LazyTree(
//        modifier = Modifier.padding(top = 4.dp),
//        tree = tree,
//        treeState = rememberTreeState(),
//        onSelectionChange = {},
//    ) { item ->
//        TreeItem(item)
//    }
//}
//
//
//
