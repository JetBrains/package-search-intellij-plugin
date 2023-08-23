package org.jetbrains.packagesearch.plugin.ui.sections.treebox

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import org.jetbrains.jewel.*
import org.jetbrains.jewel.foundation.tree.Tree
import org.jetbrains.jewel.foundation.tree.TreeState

@OptIn(ExperimentalComposeUiApi::class, ExperimentalJewelApi::class)
@Composable
fun TreeBox(
    tree: Tree<PackageSearchModuleData>,
    treeState: TreeState,
    onSelectionChange: (List<Tree.Element<PackageSearchModuleData>>) -> Unit = { },
) =
    LazyTree(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        tree = tree,
        resourceLoader = LocalResourceLoader.current,
        treeState = treeState,
        onElementClick = {},
        //onSelectionChange = onSelectionChange,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconResource = remember { it.data.module.icon.path }
            if (it !is Tree.Element.Node) {
                Spacer(modifier = Modifier.width(12.dp))
            }
            Icon(modifier = Modifier.size(16.dp), painter = painterResource(iconResource, LocalResourceLoader.current))
            Text(it.data.module.name, softWrap = false)
        }
    }

