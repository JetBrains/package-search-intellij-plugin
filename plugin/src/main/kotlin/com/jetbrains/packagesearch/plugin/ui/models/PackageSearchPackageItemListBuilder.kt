package com.jetbrains.packagesearch.plugin.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.core.utils.getIcon
import com.jetbrains.packagesearch.plugin.ui.ActionState
import com.jetbrains.packagesearch.plugin.ui.LocalIsActionPerformingState
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.DeclaredPackageMoreActionPopup
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.PackageActionLink
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.evaluateUpgrade
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.latestVersion
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.Dropdown
import org.jetbrains.jewel.DropdownState
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.styling.DropdownColors
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.LocalDropdownStyle
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
        modifyPackageContent: Content = EmptyContent,
        mainActionContent: Content = EmptyContent,
        popupContent: Content = EmptyContent,
        infoBoxDetail: InfoBoxDetail.Package,
    ) = list.add(
        PackageSearchPackageListItem.Package(
            icon = icon,
            title = title,
            subtitle = subtitle,
            id = id,
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
                    PackageActionLink("Upgrade all ($count)") {
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
                        val service = LocalPackageSearchService.current
                        Row(
                            modifier = Modifier.width(160.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            ScopeSelectionDropdown(
                                group.module.availableScopes,
                                declaredDependency.scope,
                                group.module.dependencyMustHaveAScope
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
                                ?.versions
                                ?.all
                                ?.values
                                ?.map { it.normalized }
                                ?.filter { if (onlyStable) it.isStable else true }
                                ?.let { it - declaredDependency.declaredVersion }
                                ?: emptyList()

                        val latestStable = declaredDependency.latestStableVersion
                        val latestVersion = when {
                            onlyStable && latestStable !is Missing -> latestStable
                            else -> declaredDependency.latestVersion
                        }

                        VersionSelectionDropdown(
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
                    infoBoxDetail = InfoBoxDetail.Package.DeclaredPackage(declaredDependency, group.module),
                    id = "$index ${group.id} ${declaredDependency.id}",
                    mainActionContent = {
                        val newVersion = declaredDependency.evaluateUpgrade()?.versionName
                        if (newVersion != null) {
                            PackageActionLink("Upgrade") {
                                group.dependencyManager.updateDependencies(
                                    context = it,
                                    data = listOf(declaredDependency.getUpdateData(newVersion))
                                )
                            }
                        }
                    },
                    popupContent = {
                        DeclaredPackageMoreActionPopup(group, declaredDependency)
                    }
                )
            }
        }
    }


    fun addFromRemoteGroup(
        group: PackageGroup.Remote,
        isGroupExpanded: Boolean,
        isStableOnly: Boolean,
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
                addPackage(
                    icon = apiPackage.getIcon(),
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
                    mainActionContent = {
                        val latestVersion = apiPackage.latestVersion.versionName
                        when (group) {
                            is PackageGroup.Remote.FromBaseModule -> PackageActionLink("Add") {
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
                                        ?: return@addPackage
                                val compatibleVersion = apiPackage.versions
                                    .all
                                    .asSequence()
                                    .filter { if (isStableOnly) it.value.normalized.isStable else true }
                                    .firstOrNull { firstPrimaryVariant.isCompatible(apiPackage, it.key) }
                                    ?.key
                                if (compatibleVersion != null) {
                                    PackageActionLink("Add to ${firstPrimaryVariant.name}") {
                                        group.dependencyManager.addDependency(
                                            context = it,
                                            data = firstPrimaryVariant
                                                .getInstallData(
                                                    apiPackage = apiPackage,
                                                    selectedVersion = compatibleVersion,
                                                    selectedScope = group.module.defaultScope
                                                )
                                        )
                                    }
                                }
                            }

                            is PackageGroup.Remote.FromMultipleModules -> PackageActionLink("Add") {
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
                    },
                    infoBoxDetail = InfoBoxDetail.Package.RemotePackage(apiPackage)
                )
            }
        }
    }

    fun build() = list.toList()
}


@Composable
private fun packageSearchDropdownStyle(): DropdownStyle {
    val currentStyle = LocalDropdownStyle.current
    return remember(LocalDropdownStyle.current) {
        object : DropdownStyle by currentStyle {
            override val colors: DropdownColors
                get() = object : DropdownColors by currentStyle.colors {

                    @Composable
                    override fun iconTintFor(state: DropdownState): State<Color> {
                        return if (!state.isEnabled) mutableStateOf(Color.Transparent) else super.iconTintFor(state)
                    }

                    @Composable
                    override fun backgroundFor(state: DropdownState): State<Color> {
                        return mutableStateOf(Color.Transparent)
                    }

                    @Composable
                    override fun borderFor(state: DropdownState): State<Color> {
                        return mutableStateOf(Color.Transparent)
                    }
                }
        }
    }
}

@Composable
fun ScopeSelectionDropdown(
    availableScope: List<String>,
    actualScope: String?,
    mustHaveScope: Boolean,
    updateLambda: suspend (newScope: String?) -> Unit,
) {
    val actionId = UUID.randomUUID().toString()
    var actionPerforming by LocalIsActionPerformingState.current
    val scope = LocalPackageSearchService.current.coroutineScope

    Dropdown(
        enabled = !actionPerforming.isPerforming && availableScope.isNotEmpty(),
        resourceLoader = LocalResourceLoader.current,
        style = packageSearchDropdownStyle(),
        menuContent = {
            if (!mustHaveScope && actualScope != null) {
                selectableItem(
                    selected = false,
                    onClick = {
                        actionPerforming = ActionState(true, actionId)
                        scope.launch { updateLambda(null) }
                        scope.launch {
                            delay(5.seconds)
                            if (actionPerforming.actionId == actionId) {
                                actionPerforming = ActionState(false)
                            }
                        }
                    }) {
                    Text(
                        text = PackageSearchBundle.message("packagesearch.ui.missingScope"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            availableScope.forEach {
                selectableItem(
                    selected = false,
                    onClick = {
                        actionPerforming = ActionState(true, actionId)
                        scope.launch { updateLambda(it) }
                        scope.launch {
                            delay(5.seconds)
                            if (actionPerforming.actionId == actionId) {
                                actionPerforming = ActionState(false)
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
            Text(
                text = actualScope ?: "[default]",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
    )
}


@Composable
fun VersionSelectionDropdown(
    declaredVersion: NormalizedVersion,
    availableVersions: List<NormalizedVersion>,
    latestVersion: NormalizedVersion,
    updateLambda: suspend (newScope: String) -> Unit,
) {
    val actionId = UUID.randomUUID().toString()
    var actionPerforming by LocalIsActionPerformingState.current
    val scope = LocalPackageSearchService.current.coroutineScope
    Dropdown(
        enabled = !actionPerforming.isPerforming && availableVersions.isNotEmpty(),
        resourceLoader = LocalResourceLoader.current,
        style = packageSearchDropdownStyle(),
        menuContent = {
            availableVersions.forEach {
                selectableItem(
                    selected = false,
                    onClick = {
                        actionPerforming = ActionState(true, actionId)
                        scope.launch {
                            updateLambda(it.versionName)
                        }
                        scope.launch {
                            delay(5.seconds)
                            if (actionPerforming.actionId == actionId) {
                                actionPerforming = ActionState(false)
                            }
                        }
                    }) {
                    Text(
                        modifier = Modifier.defaultMinSize(80.dp, 0.dp).padding(vertical = 4.dp),
                        textAlign = TextAlign.Center,
                        text = it.versionName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        append(availableVersions.first().versionName)
                    }
                }
            }
        }
        Row(modifier = Modifier.width(100.dp), horizontalArrangement = Arrangement.End) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

inline fun buildPackageSearchPackageItemList(builder: PackageSearchPackageItemListBuilder.() -> Unit) =
    PackageSearchPackageItemListBuilder().apply(builder).build()