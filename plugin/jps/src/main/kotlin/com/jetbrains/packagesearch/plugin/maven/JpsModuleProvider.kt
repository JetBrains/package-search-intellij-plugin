@file:Suppress("UnstableApiUsage", "CompanionObjectInExtension")

package com.jetbrains.packagesearch.plugin.maven

import com.intellij.openapi.roots.ModuleRootManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleProvider
import com.jetbrains.packagesearch.plugin.core.utils.smartModeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.intellij.openapi.module.Module as NativeModule

class JpsModuleProvider : PackageSearchModuleProvider {

    context(PackageSearchModuleBuilderContext)
    override fun provideModule(nativeModule: NativeModule): Flow<PackageSearchModule?> = nativeModule
        .project
        .smartModeFlow
        .flatMapLatest {
            if (project.hasMaven(nativeModule) || nativeModule.hasExternalSystem())
                return@flatMapLatest emptyFlow()
            getModuleChangesFlow()
                .filter { ModuleRootManager.getInstance(nativeModule).hasJava() }
                .map { nativeModule.toPackageSearch() }
        }
}

private fun ModuleRootManager.hasJava(): Boolean {
    val hasJava = sdk?.sdkType?.name == "JavaSDK"
}



