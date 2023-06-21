package org.jetbrains.packagesearch.plugin.core.extensions

import com.intellij.openapi.module.Module
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule

interface PackageSearchModuleTransformer {

    fun PolymorphicModuleBuilder<PackageSearchModule>.registerModuleSerializer()

    fun buildModule(context: PackageSearchModuleBuilderContext, nativeModule: Module): Flow<PackageSearchModule?>

    fun PolymorphicModuleBuilder<PackageSearchDeclaredPackage>.registerVersionSerializer()

}
