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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.jetbrains.packagesearch.plugin.PackageSearchBundle.message
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItem
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItem.Header.State
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent
import java.awt.Cursor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text

@Composable
fun PackageListHeader(
    additionalContentModifier: Modifier = Modifier,
    content: PackageListItem.Header,
    onEvent: (PackageListItemEvent) -> Unit,
) {
    // TODO check if needed
    val backgroundColor =
        if (JewelTheme.isDark) {
            pickComposeColorFromLaf("ToolWindow.HeaderTab.selectedInactiveBackground")
        } else {
            pickComposeColorFromLaf("Tree.selectionInactiveBackground")
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(start = 8.dp, end = 16.dp)
            .height(28.dp),
        horizontalArrangement = SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.onClick(enabled = content.state != State.LOADING) {
                    onEvent(
                        PackageListItemEvent.SetHeaderState(
                            content.id, when (content.state) {
                                State.OPEN -> PackageListItemEvent.SetHeaderState.TargetState.CLOSE
                                else -> PackageListItemEvent.SetHeaderState.TargetState.OPEN
                            }
                        )
                    )
                }
            ) {
                when (content.state) {
                    State.OPEN -> Icon(
                        resource = "general/chevron-down.svg",
                        tint = Color.Gray,
                        contentDescription = null,
                        iconClass = AllIcons::class.java
                    )

                    State.CLOSED -> Icon(
                        resource = "general/chevron-right.svg",
                        tint = Color.Gray,
                        contentDescription = null,
                        iconClass = AllIcons::class.java
                    )

                    State.LOADING -> CircularProgressIndicator()
                }
            }
            Text(
                fontWeight = FontWeight(600),
                text = content.title,
                maxLines = 1
            )
            content.count?.let { LabelInfo(text = it.toString()) }
            if (content.attriutes.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .onClick {
                            onEvent(PackageListItemEvent.InfoPanelEvent.OnHeaderAttributesClick(content.id))
                        }
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                    contentAlignment = Alignment.Center,
                ) {
                    LabelInfo(
                        text = content.attriutes.joinToString(" "),
                        maxLines = 1
                    )
                }
            }
        }
        if (content.additionalContent != null) {
            Box(
                modifier = additionalContentModifier,
            ) {

                when (content.additionalContent) {
                    is PackageListItem.Header.AdditionalContent.VariantsText ->
                        LabelInfo(
                            text = content.additionalContent.text,
                            maxLines = 1
                        )

                    is PackageListItem.Header.AdditionalContent.UpdatesAvailableCount ->
                        UpdateAllLink(content.additionalContent, content, onEvent)

                    PackageListItem.Header.AdditionalContent.Loading -> CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun UpdateAllLink(
    additionalContent: PackageListItem.Header.AdditionalContent.UpdatesAvailableCount,
    item: PackageListItem.Header,
    onEvent: (PackageListItemEvent) -> Unit,
) {
    Link(
        text = message(
            "packagesearch.ui.toolwindow.packages.actions.update.all",
            additionalContent.count
        ),
        onClick = {
            when (item.id) {
                is PackageListItem.Header.Id.Declared -> onEvent(
                    PackageListItemEvent.UpdateAllPackages(
                        item.id
                    )
                )

                else -> error("Unexpected header id: $item.id")
            }
        }
    )
}