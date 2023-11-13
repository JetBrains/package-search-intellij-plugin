package com.jetbrains.packagesearch.plugin.ui.panels.packages.items

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.onClick
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text

@Composable
fun PackageGroupHeader(
    modifier: Modifier = Modifier,
    title: String,
    badges: List<String> = emptyList(),
    groupSize: Int,
    isGroupExpanded: Boolean,
    toggleCollapse: () -> Unit,
    onBadgesClick: () -> Unit = { },
    rightContent: (@Composable () -> Unit)? = null,
) {
    val backgroundColor =
        if (JewelTheme.isDark) {
            pickComposeColorFromLaf("ToolWindow.HeaderTab.selectedInactiveBackground")
        } else {
            pickComposeColorFromLaf("Tree.selectionInactiveBackground")
        }

    Row(
        modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(start = 8.dp, end = 2.dp)
            .height(28.dp),
        horizontalArrangement = SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.onClick { toggleCollapse() }) {
                Icon(
                    resource = if (!isGroupExpanded) "general/chevron-right.svg" else "general/chevron-down.svg",
                    tint = Color.Gray,
                    contentDescription = null,
                    iconClass = AllIcons::class.java
                )
            }
            Text(
                fontWeight = FontWeight(600),
                text = title,
            )
            LabelInfo(
                text = groupSize.toString(),
            )
            if (badges.isNotEmpty()) {
                Box(
//                    modifier = Modifier.onClick {
//                        onBadgesClick() }
//                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                    contentAlignment = Alignment.Center,
                ) {
                    LabelInfo(
                        text = badges.joinToString(" "),
                    )
                }
            }
        }
        if (rightContent != null) {
            rightContent()
        }
    }
}
