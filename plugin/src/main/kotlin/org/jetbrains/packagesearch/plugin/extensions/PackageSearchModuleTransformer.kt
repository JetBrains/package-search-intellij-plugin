package org.jetbrains.packagesearch.plugin.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.plugin.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.utils.extensionsFlow

interface PackageSearchModuleTransformer {

    companion object {

        private val extensionPointName =
            ExtensionPointName<PackageSearchModuleTransformer>("org.jetbrains.packagesearch.moduleTransformer")

        val extensionsFlow
            get() = extensionPointName.extensionsFlow()
    }

    val moduleSerializer: KSerializer<out PackageSearchModule>
    val versionSerializer: KSerializer<out PackageSearchDeclaredDependency>

    fun buildModule(context: PackageSearchModuleBuilderContext, nativeModule: Module): Flow<PackageSearchModule?>

    suspend fun updateDependencies(
        context: ProjectContext,
        module: PackageSearchModule,
        installedPackages: List<PackageSearchDeclaredDependency>,
        knownRepositories: List<ApiRepository>,
        onlyStable: Boolean
    )

    suspend fun installDependency(
        context: ProjectContext,
        module: PackageSearchModule,
        apiPackage: ApiPackage,
        selectedVersion: String = apiPackage.versions.latest.versionName,
    )

    suspend fun removeDependency(
        context: ProjectContext,
        module: PackageSearchModule,
        installedPackage: PackageSearchDeclaredDependency,
    )

}