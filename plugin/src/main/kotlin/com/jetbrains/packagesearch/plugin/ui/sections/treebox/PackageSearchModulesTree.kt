package com.jetbrains.packagesearch.plugin.ui.sections.treebox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.latestVersion
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.LazyTree

@Composable
fun PackageSearchModulesTree(
    tree: Tree<PackageSearchModuleData>,
    state: TreeState,
    onSelectionChange: (List<PackageSearchModuleData>) -> Unit = { },
) {
    val scope = rememberCoroutineScope()
    Column {
        Row(
            modifier = Modifier.padding(vertical = 5.dp, horizontal = 7.dp)
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        state.openNodes = tree.walkBreadthFirst()
                            .filterIsInstance<Tree.Element.Node<PackageSearchModuleData>>()
                            .map { it.data.module.identity }
                            .toSet()
                    }
                },
            ) {
                Icon(
                    modifier = Modifier.padding(5.dp),
                    resource = "actions/expandall.svg",
                    iconClass = AllIcons::class.java,
                    contentDescription = "Collapse PKGS Tree"
                )
            }
            IconButton(
                onClick = {
                    state.openNodes = emptySet()
                },
            ) {
                Icon(
                    modifier = Modifier.padding(5.dp),
                    resource = "actions/collapseall.svg",
                    iconClass = AllIcons::class.java,
                    contentDescription = "Collapse PKGS Tree"
                )
            }

        }
        Divider(Orientation.Horizontal)
        LazyTree(
            tree = tree,
            treeState = state,
            onSelectionChange = { onSelectionChange(it.map { it.data }) },
        ) { element ->
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
                        resource = if (JewelTheme.isDark) element.data.module.icon.darkIconPath else element.data.module.icon.lightIconPath,
                        contentDescription = null,
                        iconClass = IconProvider::class.java
                    )
                    Text(
                        text = element.data.module.name,
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val hasUpdate = if (LocalIsOnlyStableVersions.current.value) {
                    element.data.module.identity.hasStableUpdates
                } else {
                    element.data.module.identity.hasUpdates
                }
                if (hasUpdate) {
                    Icon(
                        modifier = Modifier.padding(end = 20.dp),
                        resource = "icons/intui/upgradableMark.svg",
                        iconClass = IconProvider::class.java,
                        contentDescription = ""
                    )
                }
            }
        }
    }

}

