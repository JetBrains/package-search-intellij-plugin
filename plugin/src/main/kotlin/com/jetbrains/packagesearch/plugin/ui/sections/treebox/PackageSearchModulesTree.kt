package com.jetbrains.packagesearch.plugin.ui.sections.treebox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.LazyTree
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.foundation.tree.Tree
import org.jetbrains.jewel.painterResource

@Composable
fun PackageSearchModulesTree(
    tree: Tree<PackageSearchModule>,
    onSelectionChange: (List<PackageSearchModule>) -> Unit = { },
) {
    LazyTree(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        tree = tree,
        resourceLoader = LocalResourceLoader.current,
        onSelectionChange = { onSelectionChange(it.map { it.data }) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconResource = remember { it.data.icon.path }
            if (it !is Tree.Element.Node) {
                Spacer(modifier = Modifier.width(12.dp))
            }
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(iconResource, LocalResourceLoader.current),
                contentDescription = null
            )
            Text(it.data.name, softWrap = false)
        }
    }
}
