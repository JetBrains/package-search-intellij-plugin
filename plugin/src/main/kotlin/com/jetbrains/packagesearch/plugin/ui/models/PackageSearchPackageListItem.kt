package com.jetbrains.packagesearch.plugin.ui.models

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

typealias Content = @Composable () -> Unit

val EmptyContent: Content = {}

@Stable
sealed interface PackageSearchPackageListItem {

    @Stable
    data class Header(
        val title: String,
        val count: Int,
        val groupId: PackageGroup.Id,
        val badges: List<String>? = null,
        val infoBoxDetail: InfoBoxDetail.Badges? = null,
        val compatibleVariantsText: String? = null,
        val actionContent: Content? = null,
    ) : PackageSearchPackageListItem

    @Stable
    data class Package(
        val iconPath: String,
        val title: String,
        val id: String,
        val subtitle: String? = null,
        val editPackageContent: Content = EmptyContent,
        val mainActionContent: Content = EmptyContent,
        val popupContent: Content? = null,
        val infoBoxDetail: InfoBoxDetail.Package,
    ) : PackageSearchPackageListItem

}