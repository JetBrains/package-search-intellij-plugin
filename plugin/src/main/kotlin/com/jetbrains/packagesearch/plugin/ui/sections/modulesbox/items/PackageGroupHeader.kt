package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.painterResource

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
    Row(
        modifier
            .fillMaxWidth()
            .background(pickComposeColorFromLaf("Plugins.SectionHeader.background"))
            .padding(start = 8.dp, end = 2.dp)
            .height(28.dp),
        horizontalArrangement = SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.onClick { toggleCollapse() }) {
                val iconResource =
                    remember(isGroupExpanded) {
                        if (!isGroupExpanded) "icons/intui/chevronRight.svg" else "icons/intui/chevronDown.svg"
                    }
                Icon(
                    painter = painterResource(iconResource, LocalResourceLoader.current),
                    tint = Color.Gray,
                    contentDescription = null,
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
                Row(
                    modifier = Modifier.onClick { onBadgesClick() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    badges.forEach {
                        LabelInfo(
                            text = it,
                        )
                    }
                }
            }
        }
        if (rightContent != null) {
            rightContent()
        }
    }
}
