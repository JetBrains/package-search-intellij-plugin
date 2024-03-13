@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.maven

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.IconProvider.Icons
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredRepository
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.utils.toUnifiedDependency
import com.jetbrains.packagesearch.plugin.core.utils.toUnifiedRepository
import com.jetbrains.packagesearch.plugin.core.utils.validateMavenDeclaredPackageType
import com.jetbrains.packagesearch.plugin.core.utils.validateMavenPackageType
import com.jetbrains.packagesearch.plugin.core.utils.validateRepositoryType
import java.nio.file.Path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.idea.maven.dsl.MavenDependencyModificator
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType

@Serializable
@SerialName("maven")
data class PackageSearchMavenModule(
    override val name: String,
    override val identity: PackageSearchModule.Identity,
    override val buildFilePath: Path?,
    override val declaredRepositories: List<PackageSearchDeclaredMavenRepository>,
    override val declaredDependencies: List<PackageSearchDeclaredMavenPackage>,
    override val defaultScope: String? = null,
    override val availableScopes: List<String>,
    override val compatiblePackageTypes: List<PackagesType>,
    private val nativeModule: Module,
) : PackageSearchModule.Base {

    override val dependencyMustHaveAScope: Boolean
        get() = false

    override val icon
        get() = Icons.MAVEN

    override suspend fun editModule(action: context(EditModuleContext) () -> Unit) {
        writeAction { action(EditMavenModuleContext(MavenDependencyModificator(nativeModule.project))) }
    }

    context(EditModuleContext)
    override fun updateDependency(
        declaredPackage: PackageSearchDeclaredPackage,
        newVersion: String?,
        newScope: String?,
    ) {
        validateMavenDeclaredPackageType(declaredPackage)

        val oldDescriptor = declaredPackage.toUnifiedDependency()
        val newDescriptor = oldDescriptor.copy(
            coordinates = oldDescriptor.coordinates
                .copy(version = newVersion ?: oldDescriptor.coordinates.version),
            scope = newScope
        )
        modificator.updateDependency(
            module = nativeModule,
            oldDescriptor = oldDescriptor,
            newDescriptor = newDescriptor
        )
    }

    context(EditModuleContext)
    override fun addDependency(
        apiPackage: ApiPackage,
        selectedVersion: String,
        selectedScope: String?,
    ) {
        validateMavenPackageType(apiPackage)
        modificator.addDependency(
            module = nativeModule,
            descriptor = UnifiedDependency(
                groupId = apiPackage.groupId,
                artifactId = apiPackage.artifactId,
                version = selectedVersion,
                configuration = selectedScope
            )
        )
    }

    context(EditModuleContext)
    override fun removeDependency(declaredPackage: PackageSearchDeclaredPackage) {
        validateMavenDeclaredPackageType(declaredPackage)
        modificator.removeDependency(
            module = nativeModule,
            descriptor = declaredPackage.toUnifiedDependency()
        )
    }

    context(EditModuleContext)
    override fun addRepository(repository: ApiRepository) {
        validateRepositoryType(repository)
        modificator.addRepository(
            module = nativeModule,
            repository = repository.toUnifiedRepository()
        )
    }

    context(EditModuleContext)
    override fun removeRepository(repository: PackageSearchDeclaredRepository) {
        validateRepositoryType(repository)
        modificator.deleteRepository(
            module = nativeModule,
            repository = repository.toUnifiedRepository()
        )
    }
}
