package com.jetbrains.packagesearch.plugin.fus

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.api.v3.ApiRepository

sealed interface PackageSearchFUSEvent {
    data class PackageInstalled(val packageIdentifier: String, val targetModule: PackageSearchModule) :
        PackageSearchFUSEvent

    data class PackageRemoved(
        val packageIdentifier: String,
        val packageVersion: String?,
        val targetModule: PackageSearchModule,
    ) : PackageSearchFUSEvent

    data class PackageVersionChanged(
        val packageIdentifier: String,
        val packageFromVersion: String?,
        val packageTargetVersion: String,
        val targetModule: PackageSearchModule,
    ) : PackageSearchFUSEvent

    data class PackageVariantChanged(val packageIdentifier: String, val targetModule: PackageSearchModule) :
        PackageSearchFUSEvent

    data class PackageScopeChanged(
        val packageIdentifier: String,
        val scopeFrom: String?,
        val scopeTo: String?,
        val targetModule: PackageSearchModule,
    ) : PackageSearchFUSEvent

    data class RepositoryAdded(val model: ApiRepository) : PackageSearchFUSEvent
    data class RepositoryRemoved(val model: ApiRepository) : PackageSearchFUSEvent
    data object PreferencesRestoreDefaults : PackageSearchFUSEvent
    data class TargetModulesSelected(val targetModules: List<PackageSearchModule>) : PackageSearchFUSEvent
    data class PackageSelected(val isInstalled: Boolean) : PackageSearchFUSEvent
    data class DetailsLinkClick(val type: FUSGroupIds.DetailsLinkTypes) : PackageSearchFUSEvent
    data class OnlyStableToggle(val state: Boolean) : PackageSearchFUSEvent
    data class SearchRequest(val query: String) : PackageSearchFUSEvent
    data object SearchQueryClear : PackageSearchFUSEvent
    data object UpgradeAll : PackageSearchFUSEvent
    data object InfoPanelOpened : PackageSearchFUSEvent
    data class GoToSource(val module: PackageSearchModule, val packageId: String) : PackageSearchFUSEvent
    data class HeaderAttributesClick(val isSearchHeader: Boolean) : PackageSearchFUSEvent
    data object HeaderVariantsClick : PackageSearchFUSEvent
}