package com.jetbrains.packagesearch.plugin.ui.model.tree

import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule

internal data class TreeItemModel(
    val id: PackageSearchModule.Identity,
    val text: String,
    val hasUpdates: Boolean,
    val icon: IconProvider.Icon,
)