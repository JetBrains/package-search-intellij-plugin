package com.jetbrains.packagesearch.plugin.core.extensions

import com.intellij.openapi.module.Module
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import kotlinx.coroutines.flow.Flow

interface PackageSearchModuleProvider {

    context(PackageSearchModuleBuilderContext)
    fun provideModule(nativeModule: Module): Flow<PackageSearchModule?>

}