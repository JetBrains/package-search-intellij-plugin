package com.jetbrains.packagesearch.plugin.ui.sections.infobox

import OtherLinks
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.getAvailableVersions
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import com.jetbrains.packagesearch.plugin.ui.model.InfoBoxDetail
import com.jetbrains.packagesearch.plugin.ui.model.VersionSelectionDropdown
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.RemotePackageMorePopupContent
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.getLatestVersion
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

@Composable
fun RemotePackageOverviewInfo(
    selectedPackage: InfoBoxDetail.Package.RemotePackage,
    selectedModules: List<PackageSearchModuleData>,
) {
    val service = LocalPackageSearchService.current
    var openActionPopup by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 8.dp)) {

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
            PackageOverviewNameId(
                modifier = Modifier.weight(1f),
                selectedPackage.apiPackage.name,
                selectedPackage.apiPackage.id,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
                // defaultAction - can be add if only one module is selected
                if (selectedModules.size == 1) {
                    val targetModule = selectedModules.first().module
                    val defaultInstallData =
                        when (targetModule) {
                            is PackageSearchModule.Base -> targetModule

                            is PackageSearchModule.WithVariants -> targetModule.mainVariant
                        }.getInstallData(
                            apiPackage = selectedPackage.apiPackage,
                            selectedVersion = selectedPackage.apiPackage.getLatestVersion(LocalIsOnlyStableVersions.current.value),
                            selectedScope = targetModule.defaultScope
                        )
                    DefaultActionButton(PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.add.text")) {
                        selectedModules.first().dependencyManager.addDependency(
                            context = service,
                            data = defaultInstallData
                        )
                    }
                }
                // other action is add to all single module
                var backGroundColor by remember { mutableStateOf(Color.Transparent) }
                // more icon
                Box(modifier = Modifier
                    .background(backGroundColor, RoundedCornerShape(4.dp))
                    .padding(4.dp)
                    .onHover {
                        if (it) {
                            backGroundColor = pickComposeColorFromLaf("ActionButton.hoverBackground")
                        } else {
                            backGroundColor = Color.Transparent
                        }
                    }
                    .clickable {
                        openActionPopup = true
                    }
                ) {
                    Icon("general/chevron-down.svg", null, AllIcons::class.java)

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
                                Modifier.width(200.dp)
                                    .clip(shape = RoundedCornerShape(10.dp))
                                    .border(
                                        width = 1.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .background(color = backgroundColor),
                            ) {

                                RemotePackageMorePopupContent(
                                    apiPackage = selectedPackage.apiPackage,
                                    selectedModule = selectedModules.first(),
                                    onDismissRequest = { openActionPopup = false }
                                )
                            }
                        }
                    }
                }
            }
        }


    }


// versions
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            LabelInfo(
                modifier = Modifier.defaultMinSize(90.dp),
                text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.version")
            )
            val availableVersion =
                selectedPackage.apiPackage.getAvailableVersions(LocalIsOnlyStableVersions.current.value)
            val latestVersion = selectedPackage.apiPackage.getLatestVersion(LocalIsOnlyStableVersions.current.value)

            if (selectedModules.size == 1) {
                val targetModule = selectedModules.first().module
                val defaultInstallData =
                    when (targetModule) {
                        is PackageSearchModule.Base -> targetModule

                        is PackageSearchModule.WithVariants -> targetModule.mainVariant
                    }.getInstallData(
                        apiPackage = selectedPackage.apiPackage,
                        selectedVersion = selectedPackage.apiPackage.getLatestVersion(LocalIsOnlyStableVersions.current.value),
                        selectedScope = targetModule.defaultScope
                    )
                VersionSelectionDropdown(
                    declaredVersion = NormalizedVersion.Missing,
                    availableVersions = availableVersion,
                    latestVersion = latestVersion.normalized,
                    updateLambda = { newVersion ->
                        selectedModules.first().dependencyManager.addDependency(
                            context = service,
                            data = defaultInstallData
                        )
                    },
                )

            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelInfo(
                    modifier = Modifier.defaultMinSize(90.dp),
                    text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.type")
                )


                val icon = when (selectedPackage.apiPackage) {
                    is ApiMavenPackage -> IconProvider.Icons.MAVEN
                }
                val iconPath = if (JewelTheme.isDark) icon.darkIconPath else icon.lightIconPath
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(iconPath, null, IconProvider::class.java)
                    val typeName = when (selectedPackage.apiPackage) {
                        is ApiMavenPackage -> "Maven"
                    }
                    Text(typeName)
                }

            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelInfo(
                    modifier = Modifier.defaultMinSize(90.dp),
                    text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.repo")
                )
                Text(
                    selectedModules.map { it.module.declaredKnownRepositories.keys }.map { it.toString() }.distinct()
                        .joinToString(", ").removeSuffix(", ")
                )
            }

            selectedPackage.apiPackage.authors.let {
                if (it.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LabelInfo(
                            modifier = Modifier.defaultMinSize(90.dp),
                            text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.authors")
                        )
                        Text(it.map { it.name }.joinToString(", "))
                    }
                }
            }
        }
        selectedPackage.apiPackage
            .description
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                Text(modifier = Modifier.padding(2.dp), text = it)
            }

        OtherLinks(selectedPackage.apiPackage)
// licenses

    }

}


