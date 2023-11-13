
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.getAvailableVersions
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.ui.ActionType
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.model.InfoBoxDetail
import com.jetbrains.packagesearch.plugin.ui.model.ScopeSelectionDropdown
import com.jetbrains.packagesearch.plugin.ui.model.VersionSelectionDropdown
import com.jetbrains.packagesearch.plugin.ui.panels.packages.items.DeclaredPackageMoreActionPopup
import com.jetbrains.packagesearch.plugin.ui.panels.packages.items.evaluateUpgrade
import com.jetbrains.packagesearch.plugin.ui.panels.side.DefaultActionButton
import com.jetbrains.packagesearch.plugin.ui.panels.side.PackageOverviewInfo
import com.jetbrains.packagesearch.plugin.ui.panels.side.ScmLinks
import com.jetbrains.packagesearch.plugin.ui.panels.side.displayRepositoryLinks
import com.jetbrains.packagesearch.plugin.ui.panels.side.packageTypeName
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion


@Composable
fun DeclaredPackageOverviewInfo(
    selectedPackage: InfoBoxDetail.Package.DeclaredPackage,
    selectedPackageVariant: PackageSearchModule.WithVariants? = null,
) {
    val service = LocalPackageSearchService.current

    PackageOverviewInfo(
        packageName = selectedPackage.declaredDependency.displayName,
        packageId = selectedPackage.declaredDependency.id,
        customDetails = {
            DisplayPackageDetails(selectedPackage, service, selectedPackageVariant)
        },
        typeInfo = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelInfo(
                    modifier = Modifier.defaultMinSize(90.dp),
                    text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.type")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(selectedPackage.declaredDependency.icon.lightIconPath, null, IconProvider::class.java)
                    val typeName = selectedPackage
                        .declaredDependency
                        .remoteInfo
                        ?.packageTypeName
                    Text(
                        typeName
                            ?: PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.typeUnknown")
                    )
                }

            }
            val declaredVersion = selectedPackage.declaredDependency.declaredVersion
                .takeIf { it != NormalizedVersion.Missing }
            selectedPackage
                .declaredDependency
                .remoteInfo
                ?.versions
                ?.all
                ?.filter { it.normalizedVersion != NormalizedVersion.Missing }
                ?.firstOrNull { it.normalizedVersion.versionName == declaredVersion?.versionName }
                ?.let {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LabelInfo(
                            modifier = Modifier.defaultMinSize(90.dp),
                            text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.repo")
                        )
                        displayRepositoryLinks(it.repositoryIds)
                    }
                }

        },
        bottomLinks = {
            selectedPackage.declaredDependency.remoteInfo?.scm?.let { ScmLinks(it) }
        },
        licenses = selectedPackage.declaredDependency.remoteInfo?.licenses,
        authors = selectedPackage.declaredDependency.remoteInfo?.authors,
        description = selectedPackage.declaredDependency.remoteInfo?.description,
        mainActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
                // defaultAction - can be upgraded
                val newVersion = selectedPackage.declaredDependency.evaluateUpgrade()?.versionName
                if (newVersion != null) {
                    DefaultActionButton(
                        actionName = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.actions.update"),
                        actionType = ActionType.UPDATE
                    ) {
                        selectedPackage.dependencyManager.updateDependencies(
                            context = service,
                            data = listOf(
                                selectedPackage.declaredDependency.getUpdateData(
                                    newVersion
                                )
                            )
                        )
                    }
                }
            }
        },
        additionalActionsPopupContent = { onDismiss ->
            DeclaredPackageMoreActionPopup(
                dependencyManager = selectedPackage.dependencyManager,
                module = selectedPackage.module,
                packageSearchDeclaredPackage = selectedPackage.declaredDependency,
                onDismissRequest = onDismiss,
            )
        },
    )
}

@Composable
private fun DisplayPackageDetails(
    selectedPackage: InfoBoxDetail.Package.DeclaredPackage,
    service: PackageSearchProjectService,
    selectedPackageVariant: PackageSearchModule.WithVariants?,
) {
    Row(modifier = Modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        LabelInfo(
            modifier = Modifier.width(90.dp),
            text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.version")
        )
        val availableVersion =
            selectedPackage.declaredDependency.remoteInfo?.getAvailableVersions(LocalIsOnlyStableVersions.current.value)
                ?.let {
                    it - selectedPackage.declaredDependency.declaredVersion
                } ?: emptyList()
        val onlyStable = LocalIsOnlyStableVersions.current.value
        val latestStable = selectedPackage.declaredDependency.latestStableVersion
        val latestVersion = when {
            onlyStable && latestStable !is NormalizedVersion.Missing -> latestStable
            !onlyStable -> selectedPackage.declaredDependency.latestVersion
            else -> NormalizedVersion.Missing
        }

        if (latestVersion != NormalizedVersion.Missing) {
            VersionSelectionDropdown(
                dropdownModifier = Modifier.height(18.dp),
                declaredVersion = selectedPackage.declaredDependency.declaredVersion,
                availableVersions = availableVersion,
                latestVersion = latestVersion,
                updateLambda = { newVersion ->
                    selectedPackage.dependencyManager.updateDependencies(
                        context = service,
                        data = listOf(selectedPackage.declaredDependency.getUpdateData(newVersion = newVersion))
                    )
                },
            )
        } else {
            Text(
                modifier = Modifier.padding(4.dp),
                text = selectedPackage.declaredDependency.declaredVersion.versionName
            )
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LabelInfo(
            modifier = Modifier.width(90.dp),
            text = PackageSearchBundle.message("packagesearch.terminology.dependency.configuration")
        )
        ScopeSelectionDropdown(
            dropdownModifier = Modifier.height(18.dp),
            actualScope = selectedPackage.declaredDependency.scope,
            availableScope = selectedPackage.module.availableScopes,
            mustHaveScope = selectedPackage.module.dependencyMustHaveAScope,
            updateLambda = { newScope ->
                selectedPackage.dependencyManager.updateDependencies(
                    context = service,
                    data = listOf(
                        selectedPackage.declaredDependency.getUpdateData(
                            newVersion = null,
                            newScope = newScope
                        )
                    )
                )
            },
        )
    }
    if (selectedPackageVariant != null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabelInfo(
                modifier = Modifier.width(90.dp),
                text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.sourceSet")
            )
            //dropdown variant
            Text(selectedPackageVariant.mainVariant.name)
        }
    }
}

