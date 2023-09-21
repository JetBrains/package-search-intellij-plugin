package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.intellij.ui.JBColor
import java.awt.Cursor
import org.jetbrains.jewel.GlobalColors
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.LocalGlobalColors
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.OutlineColors
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.TextField
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.painterResource
import org.jetbrains.jewel.styling.LocalTextFieldStyle
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme

@Composable
fun SearchRow(
    searchQuery: String,
    searchResultsCount: Int,
    onSearchQueryChange: (String) -> Unit,
) {
    val borderColor by remember(IntUiTheme.isDark) { mutableStateOf(JBColor.border().toComposeColor()) }
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
        Icon(
            painterResource("actions/search.svg", LocalResourceLoader.current),
            contentDescription = null,
        )

        val colors = LocalGlobalColors.current
        val transparentFocus = remember(LocalGlobalColors.current) {
            object : GlobalColors by colors {
                override val outlines = object : OutlineColors by colors.outlines {
                    override val focused = Color.Transparent
                }
            }
        }

        CompositionLocalProvider(
            LocalGlobalColors provides transparentFocus,
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().weight(1f),
                undecorated = true,
                style = LocalTextFieldStyle.current,
                placeholder = {
                    Text(
                        text = "Search",
                        modifier = Modifier.padding(start = 4.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        var isHovered by remember { mutableStateOf(false) }
                        Row {
                            searchResultsCount.let {
                                Text(
                                    text = "$it ${if (it == 1) "result" else "results"}",
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                            Box(
                                Modifier
                                    .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                                    .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.DEFAULT_CURSOR)))
                                    .clip(shape = RoundedCornerShape(10.dp)),
                            ) {
                                val icon =
                                    if (isHovered) "ide/notification/closeHover.svg" else "ide/notification/closeHover.svg"
                                Icon(
                                    painter = painterResource(icon, LocalResourceLoader.current),
                                    modifier = Modifier.clickable { onSearchQueryChange("") },
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                },
            )
        }

    }
}

