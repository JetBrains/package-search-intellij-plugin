package com.jetbrains.packagesearch.plugin.ui.panels.side

import DeclaredPackageOverviewInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.model.InfoBoxDetail
import com.jetbrains.packagesearch.plugin.ui.panels.packages.getGlobalColorsWithTransparentFocusOverride
import org.jetbrains.jewel.foundation.LocalGlobalColors
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip

internal enum class InfoTabState {
    Overview,
    Platforms,
}

@Composable
fun PackageSearchInfoBox(
    infoBoxDetail: InfoBoxDetail?,
    selectedModules: List<PackageSearchModuleData>,
) {
    var selectedTab by remember {
        mutableStateOf(
            when (infoBoxDetail) {
                is InfoBoxDetail.Badges.Variant -> InfoTabState.Platforms
                is InfoBoxDetail.Badges.Search -> InfoTabState.Platforms
                else -> InfoTabState.Overview

            }
        )
    }

    Column(Modifier.padding(4.dp)) {
        if (infoBoxDetail == null) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LabelInfo(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.noItemSelected"))
            }
        } else {
            TabStrip(
                listOf(
                    TabData.Default(
                        selected = selectedTab == InfoTabState.Overview,
                        label = InfoTabState.Overview.name,
                        closable = false,
                        onClick = {
                            selectedTab = InfoTabState.Overview
                        },
                    ),
//                    TabData.Default(
//                        selected = selectedTab == InfoTabState.Platforms,
//                        label = InfoTabState.Platforms.name,
//                        closable = false,
//                        onClick = {
////                            selectedTab = InfoTabState.Platforms
//                        },
//                    ),
                ),
            )
            CompositionLocalProvider(
                LocalGlobalColors provides getGlobalColorsWithTransparentFocusOverride(),
            ) {
                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    when (selectedTab) {
                        InfoTabState.Overview -> {
                            //retrieve the dependency manager
                            when (infoBoxDetail) {
                                is InfoBoxDetail.Package.DeclaredPackage -> {
                                    DeclaredPackageOverviewInfo(selectedPackage = infoBoxDetail)
                                }

                                is InfoBoxDetail.Package.RemotePackage -> {
                                    RemotePackageOverviewInfo(
                                        selectedPackage = infoBoxDetail,
                                        selectedModules = selectedModules
                                    )
                                }
                                is InfoBoxDetail.Badges.Search -> TODO()
                                is InfoBoxDetail.Badges.Variant -> TODO()
                            }
                        }

                        InfoTabState.Platforms -> PlatformsTabContent()
                    }
                }
            }
        }
    }
}
