package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneScope

@OptIn(ExperimentalSplitPaneApi::class)
fun SplitPaneScope.defaultPKGSSplitter(
    splitterColor: Color,
    cursor: PointerIcon,
    hidden: Boolean = false
) {
    splitter {
        visiblePart {
            if (!hidden) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(splitterColor)
                )
            }
        }
        handle {
            if (!hidden) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(10.dp)
                        .markAsHandle()
                        .pointerHoverIcon(cursor)
                )
            }
        }
    }
}