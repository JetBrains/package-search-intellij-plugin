package com.jetbrains.packagesearch.plugin.ui.panels.side

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.jetbrains.packagesearch.plugin.ui.PackageSearchMetrics
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
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
    additionalActionsPopupContent: MenuScope.() -> Unit,
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
                    }
                    if (openActionPopup) {
                        PopupMenu(
                            onDismissRequest = {
                                openActionPopup = false
                                return@PopupMenu false
                            },
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.heightIn(
                                min = PackageSearchMetrics.Popups.minWidth,
                                max = PackageSearchMetrics.Popups.maxHeight
                            )
                        ) {
                            additionalActionsPopupContent()
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
            if (licenses != null) {
                LicenseLinks(licenses)
            }
            authors?.takeIf { it.isNotEmpty() }?.let { Authors(it) }
            description?.takeIf { it.isNotEmpty() }?.let {
                Text(modifier = Modifier.padding(2.dp), text = it)
            }
            bottomLinks()
        }
    }
}