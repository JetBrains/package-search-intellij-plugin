package com.jetbrains.packagesearch.plugin.core.extensions

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import kotlinx.coroutines.flow.Flow

interface PackageSearchModuleProvider {

    fun provideModule(context: PackageSearchModuleBuilderContext, nativeModule: Module): Flow<PackageSearchModule?>

    fun getSyncStateFlow(project: Project): Flow<Boolean>

}