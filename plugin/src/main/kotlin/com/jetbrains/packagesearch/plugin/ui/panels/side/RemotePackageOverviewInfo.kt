package com.jetbrains.packagesearch.plugin.ui.panels.side

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.getAvailableVersions
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.ui.ActionType
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.openLinkInBrowser
import com.jetbrains.packagesearch.plugin.ui.model.InfoBoxDetail
import com.jetbrains.packagesearch.plugin.ui.model.VersionSelectionDropdown
import com.jetbrains.packagesearch.plugin.ui.panels.packages.items.RemotePackageMorePopupContent
import com.jetbrains.packagesearch.plugin.ui.panels.packages.items.latestVersion
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

@Composable
fun RemotePackageOverviewInfo(
    selectedPackage: InfoBoxDetail.Package.RemotePackage,
    selectedModules: List<PackageSearchModuleData>,
) {
    val service = LocalPackageSearchService.current
    PackageOverviewInfo(
        packageName = selectedPackage.apiPackage.name ?: "",
        packageId = selectedPackage.apiPackage.id,
        customDetails = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelInfo(
                    modifier = Modifier.width(90.dp),
                    text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.version")
                )
                val availableVersions =
                    selectedPackage.apiPackage.getAvailableVersions(LocalIsOnlyStableVersions.current.value)
                val latestVersion = selectedPackage.apiPackage.latestVersion

                if (selectedModules.size == 1) {
                    val targetModule = selectedModules.first().module
                    val defaultInstallData =
                        when (targetModule) {
                            is PackageSearchModule.Base -> targetModule

                            is PackageSearchModule.WithVariants -> targetModule.mainVariant
                        }.getInstallData(
                            apiPackage = selectedPackage.apiPackage,
                            selectedVersion = latestVersion,
                            selectedScope = targetModule.defaultScope
                        )
                    VersionSelectionDropdown(
                        dropdownModifier = Modifier.height(16.dp),
                        declaredVersion = NormalizedVersion.Missing,
                        availableVersions = availableVersions,
                        latestVersion = latestVersion.normalized,
                        updateLambda = { newVersion ->
                            selectedModules.first().dependencyManager.addDependency(
                                context = service,
                                data = defaultInstallData
                            )
                        },
                    )

                }
            }
        },
        typeInfo = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
        },
        bottomLinks = { selectedPackage.apiPackage.scm?.let { ScmLinks(it) } },
        authors = selectedPackage.apiPackage.authors,
        description = selectedPackage.apiPackage.description,
        licenses = selectedPackage.apiPackage.licenses,
        mainActionButton = {
            if (selectedModules.size == 1) {
                val targetModule = selectedModules.first().module
                val defaultInstallData =
                    when (targetModule) {
                        is PackageSearchModule.Base -> targetModule

                        is PackageSearchModule.WithVariants -> targetModule.mainVariant
                    }.getInstallData(
                        apiPackage = selectedPackage.apiPackage,
                        selectedVersion = selectedPackage.apiPackage.latestVersion,
                        selectedScope = targetModule.defaultScope
                    )
                DefaultActionButton(
                    actionName = PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.add.text"),
                    actionType = ActionType.ADD
                ) {
                    selectedModules.first().dependencyManager.addDependency(
                        context = service,
                        data = defaultInstallData
                    )
                }
            }
        },
        additionalActionsPopupContent = { onDismiss ->
            RemotePackageMorePopupContent(
                selectedPackage = selectedPackage.apiPackage,
                group = selectedPackage.group,
                onDismissRequest = { onDismiss() }
            )
        },
    )

}


@Composable
fun displayRepositoryLinks(repositoriesIds: Set<String>) {
    val scope = rememberCoroutineScope()

    @Composable
    fun extractRepos(repositoriesIds: Set<String>): List<Pair<String, String?>> {
        val knownRepo = LocalPackageSearchService.current.knownRepositoriesStateFlow.value
        return knownRepo.filterKeys { it in repositoriesIds }
            .toList()
            .distinctBy { it.first }
            .map { (_, repo) ->
                when (repo) {
                    is ApiMavenRepository -> repo.friendlyName to repo.url
                }
            }
    }

    extractRepos(repositoriesIds).map { (name, link) ->
        link?.let {
            ExternalLink(name, onClick = {
                scope.openLinkInBrowser(link)
            })
        } ?: Text(name)
        Spacer(Modifier.width(2.dp))
    }
}




