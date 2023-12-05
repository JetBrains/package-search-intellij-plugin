package com.jetbrains.packagesearch.plugin.ui.panels.packages

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.jetbrains.packagesearch.plugin.PackageSearchBundle.message
import com.jetbrains.packagesearch.plugin.ui.PackageSearchMetrics
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.styling.LocalTextFieldStyle

@Composable
fun PackageSearchSearchBar(
    onlineSearchEnabled: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PackageSearchMetrics.searchBarHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(onlineSearchEnabled) {
            Icon(
                resource = if (it) "actions/search.svg" else "general/filter.svg",
                contentDescription = null,
                iconClass = AllIcons::class.java,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            undecorated = true,
            style = LocalTextFieldStyle.current,
            placeholder = {
                Row(modifier = Modifier.padding(start = 4.dp)) {
                    Text(text = message("packagesearch.search.search"))
                    Crossfade(targetState = onlineSearchEnabled) {
                        if (!it) {
                            Text(" " + message("packagesearch.search.filterOnly"))
                        }
                    }
                }
            },
            trailingIcon = {
                Crossfade(searchQuery.isEmpty()) {
                    if (it) return@Crossfade
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            resource = "actions/close.svg",
                            contentDescription = null,
                            iconClass = AllIcons::class.java
                        )
                    }
                }
            }
        )
    }

}
