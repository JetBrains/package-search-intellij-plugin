package com.jetbrains.packagesearch.plugin.ui.panels.side

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.intellij.icons.AllIcons
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.packagesearch.api.v3.Author
import org.jetbrains.packagesearch.api.v3.LicenseFile
import org.jetbrains.packagesearch.api.v3.Licenses

@Composable
fun PackageOverviewInfo(
    packageName: String,
    packageId: String,
    customDetails: @Composable () -> Unit,
    typeInfo: @Composable () -> Unit,
    bottomLinks: @Composable () -> Unit,
    authors: List<Author>?,
    description: String?,
    licenses: Licenses<out LicenseFile>?,
    mainActionButton: @Composable () -> Unit,
    additionalActionsPopupContent: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    var openActionPopup by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
            PackageOverviewNameId(modifier = Modifier.weight(1f), packageName, packageId)
            Row(verticalAlignment = Alignment.CenterVertically) {
                mainActionButton()
                Box(modifier = Modifier.defaultMinSize(24.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = {
                        openActionPopup = true
                    }) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            resource = "general/chevron-down.svg",
                            contentDescription = null,
                            iconClass = AllIcons::class.java
                        )
                        if (openActionPopup) {
                            val contentOffsetX = with(LocalDensity.current) { 100.dp.toPx().toInt() + 1 }
                            val contentOffsetY = with(LocalDensity.current) { 32.dp.toPx().toInt() + 1 }
                            val borderColor: Color =
                                remember(JewelTheme.isDark) { pickComposeColorFromLaf("OnePixelDivider.background") }
                            val backgroundColor: Color =
                                remember(JewelTheme.isDark) { pickComposeColorFromLaf("PopupMenu.background") }
                            Popup(
                                offset = IntOffset(-contentOffsetX, contentOffsetY),
                                onDismissRequest = { openActionPopup = false },
                                properties = PopupProperties(focusable = true),
                                onPreviewKeyEvent = { false },
                                onKeyEvent = { false },
                            ) {
                                Box(
                                    modifier =
                                    Modifier.widthIn(max=250.dp).heightIn(max=200.dp)
                                        .clip(shape = RoundedCornerShape(10.dp))
                                        .border(
                                            width = 1.dp,
                                            color = borderColor,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .background(color = backgroundColor),
                                ) {
                                    additionalActionsPopupContent() {
                                        openActionPopup = false
                                    }

                                }
                            }

                        }
                    }

                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            customDetails()
            typeInfo()
            if (licenses != null) { LicenseLinks(licenses) }
            authors?.takeIf { it.isNotEmpty() }?.let { Authors(it) }
            description?.takeIf { it.isNotEmpty() }?.let {
                Text(modifier = Modifier.padding(2.dp), text = it)
            }
            bottomLinks()
        }
    }
}