package org.jetbrains.packagesearch.plugin.core.extensions

import com.intellij.openapi.module.Module
import kotlinx.coroutines.flow.Flow
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule

interface PackageSearchModuleTransformer {

    fun buildModule(context: PackageSearchModuleBuilderContext, nativeModule: Module): Flow<PackageSearchModuleData?>

}

data class PackageSearchModuleData(
    val module: PackageSearchModule,
    val dependencyManager: PackageSearchDependencyManager
)