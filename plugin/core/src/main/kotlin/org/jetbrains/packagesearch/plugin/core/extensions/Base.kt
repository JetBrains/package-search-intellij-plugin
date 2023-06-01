package org.jetbrains.packagesearch.plugin.core.extensions

import com.intellij.openapi.module.Module
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule

sealed interface PackageSearchModuleTransformer {

    interface Base : PackageSearchModuleTransformer {
        fun PolymorphicModuleBuilder<PackageSearchModule.Base>.registerModuleSerializer()
    }

    interface WithVariants : PackageSearchModuleTransformer {
        fun PolymorphicModuleBuilder<PackageSearchModule.WithVariants>.registerModuleSerializer()
    }

    fun buildModule(context: PackageSearchModuleBuilderContext, nativeModule: Module): Flow<PackageSearchModule?>

    fun PolymorphicModuleBuilder<PackageSearchDeclaredDependency>.registerVersionSerializer()

    suspend fun updateDependencies(
        context: ProjectContext,
        module: PackageSearchModule,
        installedPackages: List<PackageSearchDeclaredDependency>,
        knownRepositories: List<ApiRepository>,
        onlyStable: Boolean,
    )

    suspend fun installDependency(
        context: ProjectContext,
        module: PackageSearchModule,
        apiPackage: ApiPackage,
        selectedVersion: String,
    )

    suspend fun removeDependency(
        context: ProjectContext,
        module: PackageSearchModule,
        installedPackage: PackageSearchDeclaredDependency,
    )
}
