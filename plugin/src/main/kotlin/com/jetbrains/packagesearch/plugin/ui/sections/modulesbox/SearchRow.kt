package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.LocalGlobalColors
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.styling.LocalTextFieldStyle

@Composable
fun SearchRow(
    searchAvailable: Boolean,
    searchQuery: String,
    searchResultsCount: Int,
    onSearchQueryChange: (String) -> Unit,
) {
    val borderColor by remember(JewelTheme.isDark) { mutableStateOf(JBColor.border().toComposeColor()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .drawBehind {
                val strokeThickness = 1.dp.toPx()
                val startY = size.height - strokeThickness
                val endX = size.width
                val capDxFix = 3f
                drawLine(
                    brush = SolidColor(borderColor),
                    start = Offset(0 + capDxFix, startY),
                    end = Offset(endX - capDxFix, startY),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(searchAvailable) {
            Icon(
                resource = "actions/search.svg",
                contentDescription = null,
                iconClass = AllIcons::class.java
            )
        }
        AnimatedVisibility(!searchAvailable) {
            Icon(
                resource = "general/filter.svg",
                contentDescription = null,
                iconClass = AllIcons::class.java
            )
        }

        CompositionLocalProvider(
            LocalGlobalColors provides getGlobalColorsWithTransparentFocusOverride(),
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().weight(1f),
                undecorated = true,
                style = LocalTextFieldStyle.current,
                placeholder = {
                    AnimatedVisibility(searchAvailable) {
                        Text(
                            text = PackageSearchBundle.message("packagesearch.search.search"),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    AnimatedVisibility(!searchAvailable) {
                        Text(
                            text = PackageSearchBundle.message("packagesearch.search.filteronly"),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        Row {
                            searchResultsCount.let {
                                val textResource =
                                    if (it == 1) "packagesearch.search.result" else "packagesearch.search.results"
                                Text(
                                    text = "$it ${PackageSearchBundle.message(textResource)}",
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    resource = "actions/close.svg",
                                    modifier = Modifier.clickable { onSearchQueryChange("") },
                                    contentDescription = null,
                                    iconClass = AllIcons::class.java
                                )
                            }
                        }
                    }
                },
            )
        }

    }
}

@Composable
fun getGlobalColorsWithTransparentFocusOverride(): GlobalColors {
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

