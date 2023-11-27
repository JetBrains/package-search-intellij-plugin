package com.jetbrains.packagesearch.plugin.ui.model

sealed interface PackageSearchToolWindowState {
    data class Loading(val message: String?) : PackageSearchToolWindowState
    data object Ready : PackageSearchToolWindowState
    data object NoModules : PackageSearchToolWindowState
}