package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.UpdatePackageData
import com.jetbrains.packagesearch.plugin.ui.LocalDependencyManagers
import com.jetbrains.packagesearch.plugin.ui.LocalGlobalPopupIdState
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.openLinkInBrowser
import com.jetbrains.packagesearch.plugin.ui.models.InfoBoxDetail
import com.jetbrains.packagesearch.plugin.ui.models.PackageGroup
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.DeclaredPackageMoreActionPopup
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.InstallPackageActionLink
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.LocalPackageRow
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.PackageActionLink
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.PackageGroupHeader
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.RemotePackageRow
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.UpgradePackageActionLink
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.evaluateUpgrade
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.getLatestVersion
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.Link
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListScope
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.items
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.painterResource

@Composable
fun ResultsSelectableLazyColumn(
    packageGroups: List<PackageGroup>,
    packageGroupState: MutableMap<PackageGroup.Id, PackageGroup.State>,
    isInfoBoxOpen: Boolean,
    onElementClick: (InfoBoxDetail?) -> Unit,
) {
    val stableOnly by LocalIsOnlyStableVersions.current
    SelectableLazyColumn(
        state = rememberSelectableLazyListState(),
        selectionMode = SelectionMode.Single,
        onSelectedIndexesChanged = {
            it.singleOrNull()
                ?.let { packageGroups.elementAt(it, packageGroupState) }
                ?.asTableItem()
                .let(onElementClick)
        }
    ) {
        packageGroups.forEach { group ->
            when (group) {
                is PackageGroup.Declared.FromBaseModule ->
                    localPackageGroup(group, isInfoBoxOpen, packageGroupState, stableOnly)

                is PackageGroup.Declared.FromVariant ->
                    localPackageGroup(group, isInfoBoxOpen, packageGroupState, onElementClick, stableOnly)

                is PackageGroup.Declared.FromModuleWithVariantsCompact ->
                    localPackageGroup(group, isInfoBoxOpen, packageGroupState, stableOnly, onElementClick)

                is PackageGroup.Remote.FromBaseModule ->
                    remotePackageGroup(group, packageGroupState)
                is PackageGroup.Remote.FromVariants ->
                    remotePackageGroup(group, packageGroupState, onElementClick)

                is PackageGroup.Remote.FromMultipleModules -> remotePackageGroup(group, packageGroupState, onElementClick)
            }
        }
    }
}

fun SelectableLazyListScope.remotePackageGroup(
    group: PackageGroup.Remote.FromMultipleModules,
    state: MutableMap<PackageGroup.Id, PackageGroup.State>,
    onBadgesClick: (InfoBoxDetail?) -> Unit
) {
    stickyHeader(group.id, "header") {
        PackageGroupHeader(
            title = "Search results",
            groupSize = group.packages.size,
            isGroupExpanded = state.isExpanded(group.id),
            toggleCollapse = { state.toggle(group.id) }
        )
    }
    if (state.isExpanded(group.id)){
        items(
            items = group.packages,
            key = { group.id.hashCode() + it.hashCode() },
            contentType = { "item" }
        ) { apiPackage ->
            RemotePackageRow(
                isActive = isActive,
                isSelected = isSelected,
                apiPackage = apiPackage,
                mainActionContent = {
                    val managers = LocalDependencyManagers.current
                    val isStableOnly by LocalIsOnlyStableVersions.current
                    PackageActionLink("Add") { context ->
                        group.modules.map { module ->
                            val updates = when (module) {
                                is PackageSearchModule.Base -> module.getInstallData(
                                    apiPackage,
                                    apiPackage.getLatestVersion(isStableOnly).versionName,
                                    module.defaultScope
                                )
                                is PackageSearchModule.WithVariants -> module.mainVariant.getInstallData(
                                    apiPackage,
                                    apiPackage.getLatestVersion(isStableOnly).versionName,
                                    module.defaultScope
                                )
                            }
                            managers.getValue(module).addDependency(context, updates)
                        }
                    }
                }
//                popupContent = { // TODO popup to add to a specific variant }
            )
        }
    }
}

fun SelectableLazyListScope.remotePackageGroup(
    group: PackageGroup.Remote.FromVariants,
    state: MutableMap<PackageGroup.Id, PackageGroup.State>,
    onBadgesClick: (InfoBoxDetail?) -> Unit
) {
    stickyHeader(group.id, "header") {
        PackageGroupHeader(
            title = "Search results",
            badges = group.badges,
            groupSize = group.packages.size,
            isGroupExpanded = state.isExpanded(group.id),
            toggleCollapse = { state.toggle(group.id) },
            rightContent = {
                LabelInfo(
                    text = "For ${group.compatibleVariants.size} variants",
                    modifier = Modifier.clickable {
                        onBadgesClick(InfoBoxDetail.SearchDetails(group))
                    }
                )
                // TODO popup with supported variants
            }
        )
    }
    if (state.isExpanded(group.id)){
        items(
            items = group.packages,
            key = { group.id.hashCode() + it.hashCode() },
            contentType = { "item" }
        ) { apiPackage ->
            RemotePackageRow(
                isActive = isActive,
                isSelected = isSelected,
                apiPackage = apiPackage,
                mainActionContent = {
                    InstallPackageActionLink(
                        apiPackage = apiPackage,
                        module = group.module,
                        dependencyManager = LocalDependencyManagers.current.getValue(group.module),
                        variant = group.compatibleVariants.first { it.isPrimary }
                    )
                }
//                popupContent = { // TODO popup to add to a specific variant }
            )
        }
    }
}

fun SelectableLazyListScope.remotePackageGroup(
    group: PackageGroup.Remote.FromBaseModule,
    state: MutableMap<PackageGroup.Id, PackageGroup.State>
) {
    stickyHeader(group.id, "header") {
        PackageGroupHeader(
            title = group.module.name,
            badges = emptyList(),
            groupSize = group.packages.size,
            isGroupExpanded = state.isExpanded(group.id),
            toggleCollapse = { state.toggle(group.id) }
        )
    }
    if (state.isExpanded(group.id)) {
        items(
            items = group.packages,
            key = { group.id.hashCode() + it.hashCode() },
            contentType = { "item" }
        ) { apiPackage ->
            RemotePackageRow(
                isActive = isActive,
                isSelected = isSelected,
                apiPackage = apiPackage,
                mainActionContent = {
                    InstallPackageActionLink(
                        apiPackage = apiPackage,
                        module = group.module,
                        dependencyManager = LocalDependencyManagers.current.getValue(group.module)
                    )
                }
            )
        }
    }
}

fun <P : PackageSearchDeclaredPackage, M : PackageSearchModule> SelectableLazyListScope.localPackageGroup(
    title: String,
    packageToUpgrade: List<UpdatePackageData>,
    isInfoBoxOpen: Boolean,
    state: MutableMap<PackageGroup.Id, PackageGroup.State>,
    declaredPackages: List<P>,
    additionalDetails: (P) -> String = { it.coordinates },
    groupId: PackageGroup.Id,
    module: M,
    badges: List<String> = emptyList(),
    onBadgesClick: () -> Unit = {},
) {
    localPackageGroup(
        title = title,
        isCompact = isInfoBoxOpen,
        state = state,
        packages = declaredPackages,
        additionalDetails = additionalDetails,
        groupId = groupId,
        module = module,
        badges = badges,
        headerRightContent = packageToUpgrade.takeIf { it.isNotEmpty() }?.let {
            {
                val dependencyManager = LocalDependencyManagers.current.getValue(module)
                PackageActionLink("Upgrade all (${it.size})") {
                    dependencyManager.updateDependencies(it, packageToUpgrade)
                }
            }
        },
        onBadgesClick = onBadgesClick
    )
}

fun <P : PackageSearchDeclaredPackage, M : PackageSearchModule> SelectableLazyListScope.localPackageGroup(
    title: String,
    isCompact: Boolean,
    state: MutableMap<PackageGroup.Id, PackageGroup.State>,
    packages: List<P>,
    additionalDetails: (P) -> String = { it.coordinates },
    groupId: PackageGroup.Id,
    module: M,
    badges: List<String> = emptyList(),
    headerRightContent: (@Composable () -> Unit)? = null,
    onBadgesClick: () -> Unit = {},
) {
    localPackageGroupHeader(groupId, title, badges, packages, state, headerRightContent, onBadgesClick)
    if (state.isExpanded(groupId)) {
        declaredPackages(packages, groupId, isCompact, additionalDetails, module)
    }
}

private fun <P : PackageSearchDeclaredPackage> SelectableLazyListScope.localPackageGroupHeader(
    groupId: PackageGroup.Id,
    title: String,
    badges: List<String>,
    packages: List<P>,
    state: MutableMap<PackageGroup.Id, PackageGroup.State>,
    headerRightContent: @Composable() (() -> Unit)?,
    onBadgesClick: () -> Unit = {},
) {
    stickyHeader(groupId, "header") {
        PackageGroupHeader(
            title = title,
            badges = badges,
            groupSize = packages.size,
            isGroupExpanded = state.isExpanded(groupId),
            toggleCollapse = { state.toggle(groupId) },
            rightContent = headerRightContent,
            onBadgesClick = onBadgesClick,
        )
    }
}

fun <P : PackageSearchDeclaredPackage> SelectableLazyListScope.declaredPackages(
    packages: List<P>,
    groupId: PackageGroup.Id,
    isCompact: Boolean,
    additionalDetails: (P) -> String,
    module: PackageSearchModule,
) {
    items(
        items = packages,
        key = { groupId.hashCode() + it.hashCode() },
        contentType = { "item" }
    ) {
        val dependencyManager = LocalDependencyManagers.current.getValue(module)
        LocalPackageRow(
            isActive = isActive,
            isSelected = isSelected,
            isCompact = isCompact,
            additionalDetails = additionalDetails(it),
            packageSearchDeclaredPackage = it,
            mainActionContent = {
                UpgradePackageActionLink(
                    packageSearchDeclaredPackage = it,
                    dependencyManager = dependencyManager,
                )
            },
            popupContent = {
                var globalPopupId by LocalGlobalPopupIdState.current
                DeclaredPackageMoreActionPopup(
                    dependencyManager = dependencyManager,
                    packageSearchDeclaredPackage = it,
                    onDismissRequest = { globalPopupId = null },
                )
            }
        )
    }
}

fun SelectableLazyListScope.localPackageGroup(
    group: PackageGroup.Declared.FromVariant,
    isInfoBoxOpen: Boolean,
    state: MutableMap<PackageGroup.Id, PackageGroup.State>,
    onBadgesClick: (InfoBoxDetail?) -> Unit,
    stableOnly: Boolean,
) {
    localPackageGroup(
        title = group.variant.name,
        packageToUpgrade = group.variant.declaredDependencies
            .mapNotNull { declared ->
                declared.evaluateUpgrade(stableOnly)
                    ?.let { declared.getUpdateData(it.versionName, declared.scope) }
            },
        isInfoBoxOpen = isInfoBoxOpen,
        state = state,
        declaredPackages = group.filteredDependencies,
        groupId = group.id,
        module = group.module,
        badges = group.variant.attributes, // TODO use badges groups
        onBadgesClick = { onBadgesClick(InfoBoxDetail.VariantDetails(group.variant)) }
    )
}

fun SelectableLazyListScope.localPackageGroup(
    group: PackageGroup.Declared.FromBaseModule,
    isInfoBoxOpen: Boolean,
    state: MutableMap<PackageGroup.Id, PackageGroup.State>,
    stableOnly: Boolean,
) {
    localPackageGroup(
        title = group.module.name,
        packageToUpgrade = group.module.declaredDependencies
            .mapNotNull { declared ->
                declared.evaluateUpgrade(stableOnly)
                    ?.let { declared.getUpdateData(it.versionName, declared.scope) }
            },
        isInfoBoxOpen = isInfoBoxOpen,
        state = state,
        declaredPackages = group.filteredDependencies,
        groupId = group.id,
        module = group.module
    )
}

fun SelectableLazyListScope.localPackageGroup(
    group: PackageGroup.Declared.FromModuleWithVariantsCompact,
    isInfoBoxOpen: Boolean,
    state: MutableMap<PackageGroup.Id, PackageGroup.State>,
    stableOnly: Boolean,
    onBadgesClick: (InfoBoxDetail?) -> Unit,
) {
    localPackageGroup(
        title = group.module.name,
        packageToUpgrade = group.filteredDependencies
            .mapNotNull { declared ->
                declared.evaluateUpgrade(stableOnly)
                    ?.let { declared.getUpdateData(it.versionName, declared.scope) }
            },
        isInfoBoxOpen = isInfoBoxOpen,
        state = state,
        declaredPackages = group.filteredDependencies,
        additionalDetails = { "${it.coordinates} (${it.variantName})" },
        groupId = group.id,
        module = group.module,
        badges = group.module.mainVariant.attributes,
        onBadgesClick = { onBadgesClick(InfoBoxDetail.VariantDetails(group.module.mainVariant)) }
    )
}

fun MutableMap<PackageGroup.Id, PackageGroup.State>.toggle(id: PackageGroup.Id) {
    this[id] = getOrDefault(id, PackageGroup.State.OPEN).toggle()
}

fun MutableMap<PackageGroup.Id, PackageGroup.State>.isExpanded(id: PackageGroup.Id): Boolean =
    getOrDefault(id, PackageGroup.State.OPEN) == PackageGroup.State.OPEN

private fun PackageGroupElement.asTableItem(): InfoBoxDetail = when (group) {
    is PackageGroup.Remote -> InfoBoxDetail.RemotePackage(group.packages[innerIndex])
    is PackageGroup.Declared -> InfoBoxDetail.DeclaredPackage(group.filteredDependencies[innerIndex])
}

data class PackageGroupElement(
    val group: PackageGroup,
    val innerIndex: Int
)

fun List<PackageGroup>.elementAt(
    index: Int,
    packageGroupState: Map<PackageGroup.Id, PackageGroup.State>
): PackageGroupElement {
    if (isEmpty() || index < 0) throw IndexOutOfBoundsException("Index: $index, Size: 0")
    var count = 0
    // each group has a header that counts as an element, but cannot be returned!
    forEach { packageGroup ->
        if (count == index) error("Index: $index is a header, not a package")
        if (packageGroupState[packageGroup.id] == PackageGroup.State.COLLAPSED) {
            count += 1
            return@forEach
        }
        if (count < index + 1 && index + 1 < count + packageGroup.size + 1) {
            return PackageGroupElement(
                group = packageGroup,
                innerIndex = index - count - 1
            )
        }
        count += packageGroup.size + 1
    }
    throw IndexOutOfBoundsException("Index: $index, Size: $count")
}

@Composable
fun NoResultsToShow() {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LabelInfo("No supported dependencies were found.")
        LabelInfo("Search to add dependencies to the project.")
        Row {
            Icon(
                painter = painterResource("icons/intui/question.svg", LocalResourceLoader.current),
                modifier = Modifier.size(16.dp).padding(end = 4.dp),
                contentDescription = null
            )
            Link(
                resourceLoader = LocalResourceLoader.current,
                text = "Learn more",
                onClick = { openLinkInBrowser("https://www.jetbrains.com/help/idea/package-search.html") }
            )

        }
    }
}
