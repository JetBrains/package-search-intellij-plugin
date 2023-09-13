package com.jetbrains.packagesearch.plugin.ui.sections.treebox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.LazyTree
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.foundation.tree.Tree
import org.jetbrains.jewel.foundation.tree.TreeState
import org.jetbrains.jewel.painterResource
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme

@Composable
fun PackageSearchModulesTree(
    tree: Tree<PackageSearchModuleData>,
    state: TreeState,
    onSelectionChange: (List<PackageSearchModuleData>) -> Unit = { },
) {
    LazyTree(
        tree = tree,
        treeState = state,
        resourceLoader = LocalResourceLoader.current,
        onSelectionChange = { onSelectionChange(it.map { it.data }) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (it !is Tree.Element.Node) {
                Spacer(modifier = Modifier.width(12.dp))
            }
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(
                    if (IntUiTheme.isDark) it.data.module.icon.darkIconPath else it.data.module.icon.lightIconPath,
                    LocalResourceLoader.current
                ),
                contentDescription = null,
            )
            Text(it.data.module.name, softWrap = false)
        }
    }
}
