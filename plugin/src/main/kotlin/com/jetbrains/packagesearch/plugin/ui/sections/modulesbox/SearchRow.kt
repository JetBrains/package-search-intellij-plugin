package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.TextField
import org.jetbrains.jewel.painterResource
import org.jetbrains.jewel.util.pxToDp
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
            painterResource("actions/search.svg", LocalResourceLoader.current),
            contentDescription = null,
        )
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            undecorated = true,
            placeholder = {
                Text(
                    text = "Search",
                    modifier = Modifier.padding(start = 4.pxToDp()),
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    var isHovered by remember { mutableStateOf(false) }
                    Row {
                        searchResultsCount.let {
                            Text(
                                text = "$it ${if (it == 1) "result" else "results"}",
                                modifier = Modifier.padding(end = 4.pxToDp()),
                            )
                        }
                        Box(
                            Modifier
                                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                                .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                                .pointerHoverIcon(PointerIcon(Cursor(Cursor.DEFAULT_CURSOR)))
                                .clip(shape = RoundedCornerShape(10.dp)),
                        ) {
                            val icon = if (isHovered) "ide/notification/closeHover.svg" else "ide/notification/closeHover.svg"
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
