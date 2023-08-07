package com.jetbrains.packagesearch.plugin.core.extensions

import com.intellij.openapi.module.Module
import kotlinx.coroutines.flow.Flow
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule

interface PackageSearchModuleProvider {

    fun provideModule(context: PackageSearchModuleBuilderContext, nativeModule: Module): Flow<PackageSearchModuleData?>

}

data class PackageSearchModuleData(
    val module: PackageSearchModule,
    val dependencyManager: PackageSearchDependencyManager
)