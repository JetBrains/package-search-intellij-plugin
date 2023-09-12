package com.jetbrains.packagesearch.plugin.ui.models

import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.DeclaredPackageMoreActionPopup
import com.jetbrains.packagesearch.plugin.core.utils.getIcon
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.PackageActionLink
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.evaluateUpgrade
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.getLatestVersion
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.latestVersion

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
            badges = if (group is PackageGroup.Declared.FromVariant) group.variant.attributes else emptyList(),
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
            group.filteredDependencies.forEach { declaredDependency ->
                addPackage(
                    icon = declaredDependency.icon,
                    title = declaredDependency.displayName,
                    subtitle = when {
                        group is PackageGroup.Declared.FromModuleWithVariantsCompact
                                && declaredDependency is PackageSearchDeclaredPackage.WithVariant -> declaredDependency.variantName

                        else -> declaredDependency.coordinates.takeIf { it != declaredDependency.displayName }
                    },
                    // TODO modify package content
                    infoBoxDetail = InfoBoxDetail.Package.DeclaredPackage(declaredDependency, group.module),
                    id = "${group.id} ${declaredDependency.id}",
                    mainActionContent = {
                        val newVersion = declaredDependency.evaluateUpgrade()?.versionName
                        if (newVersion != null) {
                            PackageActionLink("Upgrade") {
                                group.dependencyManager.updateDependencies(
                                    it,
                                    listOf(declaredDependency.getUpdateData(newVersion))
                                )
                            }
                        }
                    },
                    popupContent = {
                        DeclaredPackageMoreActionPopup(
                            group.dependencyManager,
                            declaredDependency,
                        )
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
            group.packages.forEach { apiPackage ->
                addPackage(
                    icon = apiPackage.getIcon(),
                    title = apiPackage.name ?: apiPackage.coordinates,
                    subtitle = apiPackage.coordinates.takeIf { apiPackage.name != null },
                    id = "${group.id} ${apiPackage.id}",
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
                                    group.compatibleVariants.firstOrNull { it.isPrimary }
                                        ?: group.compatibleVariants.firstOrNull()
                                        ?: return@addPackage
                                val compatibleVersion = apiPackage.versions
                                    .all
                                    .asSequence()
                                    .filter { if (isStableOnly) it.value.normalized.isStable else true }
                                    .firstOrNull { firstPrimaryVariant.isCompatible(apiPackage, it.key) }
                                    ?.key
                                if (compatibleVersion != null) {
                                    PackageActionLink("Add") {
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

inline fun buildPackageSearchPackageItemList(builder: PackageSearchPackageItemListBuilder.() -> Unit) =
    PackageSearchPackageItemListBuilder().apply(builder).build()