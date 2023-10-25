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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.jetbrains.jewel.ui.component.LazyTree

@Composable
fun PackageSearchModulesTree(
    tree: Tree<PackageSearchModuleData>,
    state: TreeState,
    onSelectionChange: (List<PackageSearchModuleData>) -> Unit = { },
) {
    LazyTree(
        tree = tree,
        treeState = state,
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
                resource = if (JewelTheme.isDark) it.data.module.icon.darkIconPath else it.data.module.icon.lightIconPath,
                contentDescription = null,
                iconClass = IconProvider::class.java
            )
            Text(
                text = it.data.module.name,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
