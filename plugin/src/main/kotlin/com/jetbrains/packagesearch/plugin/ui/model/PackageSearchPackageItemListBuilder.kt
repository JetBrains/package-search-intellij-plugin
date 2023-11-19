package com.jetbrains.packagesearch.plugin.ui.model

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.core.data.getAvailableVersions
import com.jetbrains.packagesearch.plugin.core.utils.icon
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.ui.ActionState
import com.jetbrains.packagesearch.plugin.ui.ActionType
import com.jetbrains.packagesearch.plugin.ui.LocalIsActionPerformingState
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.panels.packages.items.DeclaredPackageMoreActionsMenu
import com.jetbrains.packagesearch.plugin.ui.panels.packages.items.PackageActionLink
import com.jetbrains.packagesearch.plugin.ui.panels.packages.items.RemotePackageMoreActionsMenu
import com.jetbrains.packagesearch.plugin.ui.panels.packages.items.evaluateUpgrade
import com.jetbrains.packagesearch.plugin.ui.panels.packages.items.latestVersion
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.DropdownColors
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.theme.dropdownStyle
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion.Missing

class PackageSearchPackageItemListBuilder {
    private val list = mutableListOf<PackageSearchPackageListItem>()
    fun addHeader(
        title: String,
        count: Int,
        groupId: PackageGroup.Id,
        badges: List<String>? = null,
        infoBoxDetail: InfoBoxDetail.Badges? = null,
        compatibleVariantsText: String? = null,
        actionContent: Content? = null,
    ) = list.add(
        PackageSearchPackageListItem.Header(
            title = title,
            count = count,
            groupId = groupId,
            badges = badges,
            infoBoxDetail = infoBoxDetail,
            compatibleVariantsText = compatibleVariantsText,
            actionContent = actionContent
        )
    )

    fun addPackage(
        icon: IconProvider.Icon,
        title: String,
        subtitle: String? = null,
        id: String,
        groupId: String,
        modifyPackageContent: Content = EmptyContent,
        mainActionContent: Content = EmptyContent,
        popupContent: (MenuScope.() -> Unit)? = null,
        infoBoxDetail: InfoBoxDetail.Package,
    ) = list.add(
        PackageSearchPackageListItem.Package(
            icon = icon,
            title = title,
            subtitle = subtitle,
            id = id,
            groupId = groupId,
            editPackageContent = modifyPackageContent,
            mainActionContent = mainActionContent,
            popupContent = popupContent,
            infoBoxDetail = infoBoxDetail
        )
    )

    fun addFromDeclaredGroup(
        group: PackageGroup.Declared,
        isExpanded: Boolean,
        isStableOnly: Boolean,
        service: PackageSearchProjectService,
        isActionPerformingState: MutableState<ActionState?>,
        popupOpenState: MutableState<String?>,
    ) {
        addHeader(
            title = if (group is PackageGroup.Declared.FromVariant) group.variant.name else group.module.name,
            count = group.size,
            groupId = group.id,
            badges = if (group is PackageGroup.Declared.FromVariant) group.variant.attributes.map { it.value } else emptyList(),
            infoBoxDetail = if (group is PackageGroup.Declared.FromVariant) InfoBoxDetail.Badges.Variant(group.variant) else null,
            actionContent = {
                val count = group.filteredDependencies
                    .count { it.evaluateUpgrade() != null }
                if (count > 0) {
                    PackageActionLink(
                        text = PackageSearchBundle.message(
                            "packagesearch.ui.toolwindow.actions.upgradeAll.text.withCount",
                            count
                        ),
                        actionType = ActionType.UPDATE
                    ) {
                        val upgrades = group.filteredDependencies.mapNotNull {
                            val newVersion = it.evaluateUpgrade(isStableOnly)?.versionName
                                ?: return@mapNotNull null
                            it.getUpdateData(newVersion)
                        }
                        group.dependencyManager.updateDependencies(it, upgrades)
                    }
                }
            }
        )
        if (isExpanded) {
            group.filteredDependencies.forEachIndexed { index, declaredDependency ->
                addPackage(
                    icon = declaredDependency.icon,
                    title = declaredDependency.displayName,
                    subtitle = when {
                        group is PackageGroup.Declared.FromModuleWithVariantsCompact
                                && declaredDependency is PackageSearchDeclaredPackage.WithVariant -> declaredDependency.variantName

                        else -> declaredDependency.coordinates.takeIf { it != declaredDependency.displayName }
                    },
                    modifyPackageContent = {
                        Row(
                            modifier = Modifier.width(160.dp).height(40.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            ScopeSelectionDropdown(
                                contentModifier = Modifier.defaultMinSize(90.dp),
                                availableScope = group.module.availableScopes,
                                actualScope = declaredDependency.scope,
                                mustHaveScope = group.module.dependencyMustHaveAScope
                            ) { newScope: String? ->
                                group.dependencyManager.updateDependencies(
                                    context = service,
                                    data = listOf(
                                        declaredDependency.getUpdateData(
                                            newVersion = null,
                                            newScope = newScope
                                        )
                                    )
                                )
                            }
                        }

                        val onlyStable = LocalIsOnlyStableVersions.current.value
                        val declaredVersion = declaredDependency.declaredVersion
                        val availableVersions =
                            declaredDependency.remoteInfo
                                ?.getAvailableVersions(onlyStable)
                                ?.let { it - declaredDependency.declaredVersion }
                                ?: emptyList()

                        val latestStable = declaredDependency.latestStableVersion
                        val latestVersion = when {
                            onlyStable && latestStable !is Missing -> latestStable
                            !onlyStable -> declaredDependency.latestVersion
                            else -> Missing
                        }

                        VersionSelectionDropdown(
                            contentModifier = Modifier.defaultMinSize(160.dp),
                            declaredVersion = declaredVersion,
                            availableVersions = availableVersions,
                            latestVersion = latestVersion
                        ) { newVersion: String ->
                            group.dependencyManager.updateDependencies(
                                context = service,
                                data = listOf(declaredDependency.getUpdateData(newVersion = newVersion))
                            )
                        }
                    },
                    infoBoxDetail = InfoBoxDetail.Package.DeclaredPackage(
                        declaredDependency,
                        group.module,
                        group.dependencyManager,
                    ),
                    id = declaredDependency.id,
                    groupId = group.id.value,
                    mainActionContent = {
                        DeclaredDependencyMainActionContent(declaredDependency, group.dependencyManager)
                    },
                    popupContent = {
                        DeclaredPackageMoreActionsMenu(
                            group.dependencyManager,
                            group.module,
                            packageSearchDeclaredPackage = declaredDependency,
                            service = service,
                            isActionPerformingState = isActionPerformingState,
                            popupOpenState = popupOpenState,
                        )
                    }
                )
            }
        }
    }


    fun addFromRemoteGroup(
        group: PackageGroup.Remote,
        isGroupExpanded: Boolean,
        service: PackageSearchProjectService,
        isActionPerformingState: MutableState<ActionState?>,
        isOnlyStable: Boolean,
    ) {
        val compatibleVariantsText = if (group is PackageGroup.Remote.FromVariants) {
            val cardinality = group.compatibleVariants.size
            val terminology =
                group.module.mainVariant.variantTerminology
                    ?: PackageSearchModuleVariant.Terminology.DEFAULT
            "For $cardinality ${terminology.getForCardinality(cardinality)}"
        } else null
        val infoBoxDetail = if (group is PackageGroup.Remote.FromVariants)
            InfoBoxDetail.Badges.Search(group)
        else null
        addHeader(
            title = "Search result",
            count = group.size,
            groupId = group.id,
            badges = if (group is PackageGroup.Remote.FromVariants) group.badges else emptyList(),
            infoBoxDetail = infoBoxDetail,
            compatibleVariantsText = compatibleVariantsText
        )
        if (isGroupExpanded) {
            group.packages.forEachIndexed { index, apiPackage ->
                val mainActionContent: @Composable () -> Unit = {
                    val latestVersion = apiPackage.latestVersion
                    when (group) {
                        is PackageGroup.Remote.FromBaseModule -> PackageActionLink(
                            text = PackageSearchBundle.message(
                                "packagesearch.ui.toolwindow.packages.actions.install"
                            ),
                            actionType = ActionType.ADD
                        ) {
                            group.dependencyManager.addDependency(
                                context = it,
                                data = group.module.getInstallData(
                                    apiPackage = apiPackage,
                                    selectedVersion = latestVersion,
                                    selectedScope = group.module.defaultScope
                                )
                            )
                        }

                        is PackageGroup.Remote.FromVariants -> {
                            val firstPrimaryVariant =
                                group.compatibleVariants
                                    .firstOrNull { it.isPrimary && it.declaredDependencies.none { it.id == apiPackage.id } }
                                    ?: group.compatibleVariants
                                        .firstOrNull { it.declaredDependencies.none { it.id == apiPackage.id } }
                            if (firstPrimaryVariant != null) {
                                PackageActionLink(
                                    PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.add.text"),
                                    ActionType.ADD
                                ) {
                                    group.dependencyManager.addDependency(
                                        context = it,
                                        data = firstPrimaryVariant
                                            .getInstallData(
                                                apiPackage = apiPackage,
                                                selectedVersion = latestVersion,
                                                selectedScope = group.module.defaultScope
                                            )
                                    )
                                }
                            }
                        }

                        is PackageGroup.Remote.FromMultipleModules -> PackageActionLink(
                            PackageSearchBundle.message(
                                "packagesearch.ui.toolwindow.packages.actions.install"
                            ),
                            ActionType.ADD
                        ) {
                            group.moduleData
                                .forEach { (module, dependencyManager) ->
                                    dependencyManager.addDependency(
                                        context = it,
                                        data = when (module) {
                                            is PackageSearchModule.Base ->
                                                module.getInstallData(
                                                    apiPackage = apiPackage,
                                                    selectedVersion = latestVersion,
                                                    selectedScope = module.defaultScope
                                                )

                                            is PackageSearchModule.WithVariants ->
                                                module.mainVariant.getInstallData(
                                                    apiPackage = apiPackage,
                                                    selectedVersion = latestVersion,
                                                    selectedScope = module.defaultScope
                                                )
                                        }
                                    )
                                }
                        }
                    }
                }
                val infoBoxDetail = InfoBoxDetail.Package.RemotePackage(apiPackage, group)
                addPackage(
                    icon = apiPackage.icon,
                    title = apiPackage.name ?: apiPackage.coordinates,
                    subtitle = apiPackage.coordinates.takeIf { apiPackage.name != null },
                    id = buildString {
                        append(index)
                        append(" ")
                        append(group.id)
                        append(" ")
                        append(apiPackage.id)
                        append(" ")
                        if (group is PackageGroup.Remote.FromVariants) {
                            append(
                                group.compatibleVariants
                                    .filter { it.declaredDependencies.any { apiPackage.id == it.id } }
                                    .joinToString { it.name }
                            )
                        }
                    },
                    groupId = "remote.${group.id.value}.${apiPackage.id}",
                    popupContent = {
                        RemotePackageMoreActionsMenu(
                            apiPackage = apiPackage,
                            group = group,
                            isActionPerformingState = isActionPerformingState,
                            service = service,
                            isOnlyStable = isOnlyStable,
                        )
                    },
                    mainActionContent = mainActionContent,
                    infoBoxDetail = infoBoxDetail
                )
            }
        }
    }

    fun build() = list.toList()
}


@Composable
private fun packageSearchDropdownStyle(): DropdownStyle {
    val currentStyle = JewelTheme.dropdownStyle
    return DropdownStyle(
        colors = DropdownColors(
            background = Color.Transparent,
            backgroundDisabled = Color.Transparent,
            backgroundFocused = Color.Transparent,
            backgroundPressed = Color.Transparent,
            backgroundHovered = Color.Transparent,
            content = currentStyle.colors.content,
            contentDisabled = currentStyle.colors.contentDisabled,
            contentFocused = currentStyle.colors.contentFocused,
            contentPressed = currentStyle.colors.contentPressed,
            contentHovered = currentStyle.colors.contentHovered,
            border = Color.Transparent,
            borderDisabled = Color.Transparent,
            borderFocused = Color.Transparent,
            borderPressed = Color.Transparent,
            borderHovered = Color.Transparent,
            iconTintDisabled = Color.Transparent,
            iconTint = currentStyle.colors.iconTint,
            iconTintFocused = currentStyle.colors.iconTintFocused,
            iconTintPressed = currentStyle.colors.iconTintPressed,
            iconTintHovered = currentStyle.colors.iconTintHovered,
        ),
        metrics = currentStyle.metrics,
        icons = currentStyle.icons,
        textStyle = currentStyle.textStyle,
        menuStyle = currentStyle.menuStyle,
    )
}

@Composable
fun ScopeSelectionDropdown(
    dropdownModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    availableScope: List<String>,
    actualScope: String?,
    mustHaveScope: Boolean,
    updateLambda: suspend (newScope: String?) -> Unit,
) {
    val actionId = UUID.randomUUID().toString()
    var actionPerforming by LocalIsActionPerformingState.current
    val scope = LocalPackageSearchService.current.coroutineScope

    Dropdown(
        modifier = dropdownModifier,
        menuModifier = Modifier.heightIn(max = 100.dp),
        enabled = actionPerforming == null && availableScope.isNotEmpty(),
        style = packageSearchDropdownStyle(),
        menuContent = {
            if (!mustHaveScope && actualScope != null) {
                selectableItem(
                    selected = false,
                    onClick = {
                        actionPerforming = ActionState(true, ActionType.UPDATE, actionId)
                        scope.launch { updateLambda(null) }
                        scope.launch {
                            delay(5.seconds)
                            if (actionPerforming?.actionId == actionId) {
                                actionPerforming = null
                            }
                        }
                    }) {
                    Text(
                        text = PackageSearchBundle.message("packagesearch.ui.missingScope"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                }
            }
            availableScope.forEach {
                selectableItem(
                    selected = false,
                    onClick = {
                        actionPerforming = ActionState(true, ActionType.UPDATE, actionId)
                        scope.launch { updateLambda(it) }
                        scope.launch {
                            delay(5.seconds)
                            if (actionPerforming?.actionId == actionId) {
                                actionPerforming = null
                            }
                        }
                    }) {
                    Text(
                        text = it,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                }
            }
        },
        content = {
            Row(modifier = contentModifier, horizontalArrangement = Arrangement.End) {
                Text(
                    text = actualScope ?: PackageSearchBundle.message("packagesearch.ui.missingScope"),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.End
                )
            }
        },
    )
}


@Composable
fun VersionSelectionDropdown(
    dropdownModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    declaredVersion: NormalizedVersion,
    availableVersions: List<NormalizedVersion>,
    latestVersion: NormalizedVersion,
    updateLambda: suspend (newVersion: String) -> Unit,
) {
    val actionId = UUID.randomUUID().toString()
    var actionPerforming by LocalIsActionPerformingState.current
    val scope = LocalPackageSearchService.current.coroutineScope
    Dropdown(
        modifier = dropdownModifier,
        menuModifier = Modifier.heightIn(max = 150.dp),
        enabled = actionPerforming == null && availableVersions.isNotEmpty(),
        style = packageSearchDropdownStyle(),
        menuContent = {
            availableVersions.sortedDescending().forEach {
                selectableItem(
                    selected = false,
                    onClick = {
                        actionPerforming = ActionState(true, ActionType.UPDATE, actionId)
                        scope.launch {
                            updateLambda(it.versionName)
                        }
                        scope.launch {
                            delay(5.seconds)
                            if (actionPerforming?.actionId == actionId) {
                                actionPerforming = null
                            }
                        }
                    }) {
                    Text(
                        modifier = contentModifier.padding(vertical = 4.dp),
                        textAlign = TextAlign.End,
                        text = it.versionName,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        },
    ) {
        val text = buildString {
            when {
                declaredVersion is Missing -> append(PackageSearchBundle.message("packagesearch.ui.missingVersion"))
                else -> {
                    append(declaredVersion.versionName)
                    if (latestVersion > declaredVersion) {
                        append(" → ")
                        append(latestVersion.versionName)
                    }
                }
            }
        }
        Row(modifier = contentModifier, horizontalArrangement = Arrangement.End) {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.End
            )
        }
    }
}


@Composable
internal fun DeclaredDependencyMainActionContent(
    declaredDependency: PackageSearchDeclaredPackage,
    dependencyManager: PackageSearchDependencyManager,
) {
    val newVersion = declaredDependency.evaluateUpgrade()?.versionName
    if (newVersion != null) {
        PackageActionLink(
            PackageSearchBundle.message(
                "packagesearch.ui.toolwindow.packages.actions.upgrade"
            ),
            ActionType.UPDATE
        ) {
            dependencyManager.updateDependencies(
                context = it,
                data = listOf(declaredDependency.getUpdateData(newVersion))
            )
        }
    }
}

inline fun buildPackageSearchPackageItemList(builder: PackageSearchPackageItemListBuilder.() -> Unit) =
    PackageSearchPackageItemListBuilder().apply(builder).build()