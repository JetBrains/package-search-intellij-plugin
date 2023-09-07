package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.*
import org.jetbrains.jewel.util.pxToDp
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import java.awt.Cursor

@Composable
fun SearchRow(
    searchQuery: String,
    searchResultsCount: Int,
    onSearchQueryChange: (String) -> Unit,
) {
    val borderColor = pickComposeColorFromLaf("IntelliJTheme.colors.borders.disabled")
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
            painterResource("icons/intui/search.svg", LocalResourceLoader.current),
            contentDescription = null
        )
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            undecorated = true,
            placeholder = {
                Text(
                    text = "Search",
                    modifier = Modifier.padding(start = 4.pxToDp())
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    var isHovered by remember { mutableStateOf(false) }
                    Row {
                        searchResultsCount.let {
                            Text(
                                text = "$it ${if (it == 1) "result" else "results"}",
                                modifier = Modifier.padding(end = 4.pxToDp())
                            )
                        }
                        Box(
                            Modifier
                                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                                .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                                .pointerHoverIcon(PointerIcon(Cursor(Cursor.DEFAULT_CURSOR)))
                                .clip(shape = RoundedCornerShape(10.dp))
                        ) {
                            val icon = if (isHovered) "icons/intui/closeHovered.svg" else "icons/intui/close.svg"
                            Icon(
                                painter = painterResource(icon, LocalResourceLoader.current),
                                modifier = Modifier.clickable { onSearchQueryChange("") },
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        )
    }
}
