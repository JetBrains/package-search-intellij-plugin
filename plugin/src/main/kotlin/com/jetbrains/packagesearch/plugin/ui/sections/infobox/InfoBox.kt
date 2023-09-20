package com.jetbrains.packagesearch.plugin.ui.sections.infobox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.latestStableOrNull
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.openLinkInBrowser
import com.jetbrains.packagesearch.plugin.ui.models.InfoBoxDetail
import kotlinx.coroutines.launch
import org.jetbrains.jewel.Dropdown
import org.jetbrains.jewel.ExternalLink
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.TabData
import org.jetbrains.jewel.TabStrip
import org.jetbrains.jewel.Text
import org.jetbrains.packagesearch.api.v3.ApiPackage

internal enum class InfoTabState {
    Overview,
    Platforms,
}

@Composable
fun PackageSearchInfoBox(
    selectedPackage: InfoBoxDetail?,
    selectedModules: List<PackageSearchModule>,
) {
    var selectedTab by remember { mutableStateOf(InfoTabState.Overview) }

    Column(Modifier.padding(4.dp)) {
        if (selectedPackage == null) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LabelInfo("No item selected.\nSelect a row to view details.")
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
                        tabIconResource = null,
                    ),
                    TabData.Default(
                        selected = selectedTab == InfoTabState.Platforms,
                        label = InfoTabState.Platforms.name,
                        closable = false,
                        onClick = {
                            selectedTab = InfoTabState.Platforms
                        },
                        tabIconResource = null,
                    ),
                ),
            )

            Box(Modifier.fillMaxWidth()) {
                when (selectedTab) {
                    InfoTabState.Overview -> {
                        // todo
                    }

                    InfoTabState.Platforms -> PlatformsTabContent()
                }
            }
        }
    }
}

@Composable
fun PackageOverviewInfo(
    selectedPackage: ApiPackage,
    selectedModules: List<PackageSearchModuleData>,
) {
// name action and descriptions
    var selectedVersion by remember { mutableStateOf(selectedPackage.versions.latestStable?.normalized) }
    val availableOtherVersions = remember(selectedVersion) {
        selectedPackage.versions.all.values
            .filter { it.normalized.versionName != selectedVersion?.versionName }
    }

    val projectService = LocalPackageSearchService.current
    val actions by derivedStateOf {
//        selectedPackage.getPackageActions(selectedModules, projectService, selectedModules.first().dependencyManager)
    }

//    PackageNameAndActions(
//        selectedPackage.name ?: selectedPackage.id,
//        if (selectedPackage.name.isNullOrEmpty()) "" else selectedPackage.id,
//        PackageQuality.Good,
// //        actions.first,
// //        actions.second
//    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PackageOverviewInfo(
    selectedPackage: PackageSearchDeclaredPackage,
    selectedPackageVariant: PackageSearchModule.WithVariants? = null,
    selectedModules: List<PackageSearchModuleData>,
) {
    val scope = rememberCoroutineScope()
    var selectedVersion by remember { mutableStateOf(selectedPackage.declaredVersion) }
    val availableOtherVersions = remember(selectedVersion) {
        selectedPackage.remoteInfo?.versions?.all?.values
            ?.filter { it.normalized.versionName != selectedPackage.declaredVersion.versionName }
            ?: emptyList()
    }

    var selectedScope: String? by remember { mutableStateOf(null) }

    val projectService = LocalPackageSearchService.current
    val actions by derivedStateOf {
//        selectedPackage.getPackageActions(selectedModules, projectService, selectedVersion.versionName, selectedScope)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // name action and descriptions
//        PackageNameAndActions(
//            selectedPackage.displayName,
//            selectedPackage.id,
//            PackageQuality.Good,
// //            actions.first,
// //            actions.second
//        )

        // versions
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelInfo(modifier = Modifier.defaultMinSize(90.dp), text = "Version:")
                val latestVersion = selectedPackage.latestStableOrNull?.normalized
                latestVersion?.versionName?.let {
                    selectedVersion = latestVersion
                    Dropdown(
                        resourceLoader = LocalResourceLoader.current,
                        menuContent = {
                            availableOtherVersions.forEach {
                                selectableItem(
                                    selected = it.normalized == selectedVersion,
                                    onClick = {
                                        selectedVersion = it.normalized
                                    },
                                ) {
                                    Text(selectedPackage.declaredVersion.versionName + "→" + it.normalized.versionName)
                                }
                            }
                        },
                    ) {
                        Text(selectedPackage.declaredVersion.versionName + "→" + latestVersion.versionName)
                    }
                } ?: Text(selectedPackage.declaredVersion.versionName)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelInfo(modifier = Modifier.defaultMinSize(90.dp), text = "Configuration:")
                Dropdown(
                    resourceLoader = LocalResourceLoader.current,
                    menuContent = {
                        selectedModules.map { it.module.availableScopes }.flatten().toSet().forEach {
                            selectableItem(selected = selectedScope == it, onClick = {
                                selectedScope = it
                            }) {
                                Text(it)
                            }
                        }
                    },
                ) {
                    Text(selectedScope ?: "Default")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelInfo(modifier = Modifier.defaultMinSize(90.dp), text = "Source set:")
                Text("TODO")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelInfo(modifier = Modifier.defaultMinSize(90.dp), text = "Type:")
                Text("TODO")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelInfo(modifier = Modifier.defaultMinSize(90.dp), text = "Repositories:")
                Text("TODO")
            }

            selectedPackage.remoteInfo?.authors.let {
                if (!it.isNullOrEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LabelInfo(modifier = Modifier.defaultMinSize(90.dp), text = "Authors:")
                        Text(it.map { it.name }.joinToString(", "))
                    }
                }
            }
        }
        selectedPackage.remoteInfo
            ?.description
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                Text(it)
            }
        // licenses
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            selectedPackage.remoteInfo?.licenses?.let {
                ExternalLink(
                    "License: " + (it.mainLicense.name ?: "go to license"),
                    resourceLoader = LocalResourceLoader.current,
                    onClick = {
                        scope.launch {
                            openLinkInBrowser(it.mainLicense.htmlUrl ?: it.mainLicense.url)
                        }
                    },
                )
                it.otherLicenses.takeIf { it.isNotEmpty() }?.forEach { otherLicense ->
                    Text(", ")
                    ExternalLink(
                        otherLicense.name ?: otherLicense.url,
                        resourceLoader = LocalResourceLoader.current,
                        onClick = {
                            scope.launch {
                                openLinkInBrowser(otherLicense.htmlUrl ?: otherLicense.url)
                            }
                        },
                    )
                }
            }
        }
    }
}
