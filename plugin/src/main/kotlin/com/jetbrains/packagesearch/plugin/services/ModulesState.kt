package com.jetbrains.packagesearch.plugin.services

import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData

sealed interface ModulesState {

    data object Loading : ModulesState
    data class Ready(val moduleData: List<PackageSearchModuleData>) : ModulesState
    data object NoModules : ModulesState
}