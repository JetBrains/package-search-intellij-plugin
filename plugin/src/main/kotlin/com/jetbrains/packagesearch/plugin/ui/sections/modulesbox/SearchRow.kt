package org.jetbrains.packagesearch.plugin.ui.sections.modulesbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import kotlinx.coroutines.delay
import org.jetbrains.jewel.*
import org.jetbrains.jewel.util.pxToDp
import java.awt.Cursor

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchRow(
    textSearchState: MutableState<String>, // for the initial state no search will be called
    dependenciesBrowsingModeStatus: MutableState<DependenciesBrowsingMode> = remember {
        mutableStateOf(
            DependenciesBrowsingMode.Search
        )
    },
    searchResultsCount: Int,
    debounceTimeMillis: Long = 300,
    onQueryChanged: (String) -> Unit,
) {
    LaunchedEffect(textSearchState.value) {//launched effect, once called the last will be canceled
        val currentValue = textSearchState.value
        if (currentValue.isNotEmpty()) {
            delay(debounceTimeMillis)  // debounce time in milliseconds
            if (currentValue == textSearchState.value) {//if user stop type this become true
                if (dependenciesBrowsingModeStatus.value == DependenciesBrowsingMode.Lookup) {
                    dependenciesBrowsingModeStatus.value = DependenciesBrowsingMode.Search
                }
                onQueryChanged(currentValue)
            }
        } else {
            if (dependenciesBrowsingModeStatus.value == DependenciesBrowsingMode.Search) {
                dependenciesBrowsingModeStatus.value = DependenciesBrowsingMode.Lookup
            }
        }
    }
    val borderColor = IntelliJTheme.colors.borders.disabled
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
            tint = IntelliJTheme.colors.infoContent
        )
        TextField(
            value = textSearchState.value,
            onValueChange = { textSearchState.value = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            undecorated = true,
            placeholder = {
                Text(
                    text = "Search",
                    color = IntelliJTheme.colors.infoContent,
                    modifier = Modifier.padding(start = 4.pxToDp())
                )
            },
            trailingIcon = {
                if (textSearchState.value.isNotEmpty()) {
                    var isHovered by remember { mutableStateOf(false) }
                    Row {
                        searchResultsCount.let {
                            Text(
                                text = "$it ${if (it == 1) "result" else "results"}",
                                color = IntelliJTheme.colors.infoContent,
                                modifier = Modifier.padding(end = 4.pxToDp())
                            )
                        }
                        Box(
                            Modifier
                                .onPointerEvent(PointerEventType.Enter) {
                                    isHovered = true
                                }
                                .onPointerEvent(PointerEventType.Exit) {
                                    isHovered = false
                                }
                                .pointerHoverIcon(PointerIcon(Cursor(Cursor.DEFAULT_CURSOR)))
                                .clip(shape = RoundedCornerShape(10.dp))
                        ) {
                            val icon = if (isHovered) "icons/intui/closeHovered.svg" else "icons/intui/close.svg"
                            Icon(
                                painter = painterResource(icon, LocalResourceLoader.current),
                                modifier = Modifier.clickable { textSearchState.value = "" })
                        }
                    }
                }
            }
        )
    }
}
