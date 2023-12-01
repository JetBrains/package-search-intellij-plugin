package com.jetbrains.packagesearch.plugin.ui.panels.packages

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.jetbrains.packagesearch.plugin.PackageSearchBundle.message
import com.jetbrains.packagesearch.plugin.ui.PackageSearchMetrics
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.LocalGlobalColors
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.styling.LazyTreeMetrics
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultTabStyle
import org.jetbrains.jewel.ui.component.styling.LocalLazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.LocalTextFieldStyle
import org.jetbrains.jewel.ui.component.styling.TabMetrics
import org.jetbrains.jewel.ui.component.styling.TabStyle

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

@Composable
internal fun packageSearchTabStyle(): TabStyle {
    val current = LocalDefaultTabStyle.current
    return TabStyle(
        colors = current.colors,
        metrics = TabMetrics(
            underlineThickness = current.metrics.underlineThickness,
            tabPadding = current.metrics.tabPadding,
            tabHeight = PackageSearchMetrics.searchBarHeight,
            closeContentGap = current.metrics.closeContentGap,
        ),
        icons = current.icons,
        contentAlpha = current.contentAlpha
    )
}


@Composable
fun packageSearchGlobalColors(): GlobalColors {
    val colors = LocalGlobalColors.current

    return remember(colors) {
        GlobalColors(
            borders = colors.borders,
            outlines = OutlineColors(
                focused = Color.Transparent,
                focusedWarning = colors.outlines.focusedWarning,
                focusedError = colors.outlines.focusedError,
                warning = colors.outlines.warning,
                error = colors.outlines.error,
            ),
            infoContent = colors.infoContent,
            paneBackground = colors.paneBackground,
        )
    }
}


@Composable
internal fun PackageSearchTreeStyle(): LazyTreeStyle {
    val paddings = LocalLazyTreeStyle.current.metrics.elementPadding
    return LazyTreeStyle(
        LocalLazyTreeStyle.current.colors,
        metrics = LazyTreeMetrics(
            indentSize = LocalLazyTreeStyle.current.metrics.indentSize,
            elementPadding = PaddingValues(
                top = paddings.calculateTopPadding(),
                bottom = paddings.calculateBottomPadding(),
                start = paddings.calculateStartPadding(LocalLayoutDirection.current),
                end = 0.dp
            ),
            elementContentPadding = LocalLazyTreeStyle.current.metrics.elementContentPadding,
            elementMinHeight = LocalLazyTreeStyle.current.metrics.elementMinHeight,
            chevronContentGap = LocalLazyTreeStyle.current.metrics.chevronContentGap,
            elementBackgroundCornerSize = LocalLazyTreeStyle.current.metrics.elementBackgroundCornerSize,
        ),
        LocalLazyTreeStyle.current.icons,
    )
}


