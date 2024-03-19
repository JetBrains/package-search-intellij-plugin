@file:Suppress("UnstableApiUsage", "DeprecatedCallableAddReplaceWith")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.packageSearch.mppDependencyUpdater.MppDependencyModifier
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.IconProvider.Icons
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredRepository
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.utils.toUnifiedRepository
import com.jetbrains.packagesearch.plugin.core.utils.validateRepositoryType
import com.jetbrains.packagesearch.plugin.gradle.utils.toUnifiedRepository
import com.jetbrains.packagesearch.plugin.gradle.utils.validateRepositoryType
import java.nio.file.Path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType

@Serializable
@SerialName("kotlinMultiplatform")
data class PackageSearchKotlinMultiplatformModule(
    override val name: String,
    override val identity: PackageSearchModule.Identity,
    override val buildFilePath: Path?,
    override val declaredRepositories: List<PackageSearchGradleDeclaredRepository>,
    override val variants: Map<String, PackageSearchKotlinMultiplatformVariant>,
    val packageSearchModel: PackageSearchGradleModel,
    val availableKnownRepositories: Map<String, ApiRepository>,
    val nativeModule: Module,
) : PackageSearchModule.WithVariants {

    companion object {
        val TERMINOLOGY = PackageSearchModule.WithVariants.Terminology("variant", "variants")
    }


    override val variantTerminology: PackageSearchModule.WithVariants.Terminology
        get() = TERMINOLOGY

    override val dependencyMustHaveAScope: Boolean
        get() = true

    override val icon
        get() = Icons.KOTLIN

    override suspend fun editModule(action: EditModuleContext.() -> Unit) {
        val ctx = writeAction {
            val modifier = DependencyModifierService.getInstance(nativeModule.project)
            val context = EditKMPModuleContext(EditKMPModuleContextData(modifier, nativeModule))
            action(context)
            context.data
        }
        ctx.getRemoves()
            .takeIf { it.isNotEmpty() }
            ?.let { MppDependencyModifier.removeDependencies(nativeModule, it) }
        ctx.getInstalls()
            .takeIf { it.isNotEmpty() }
            ?.let { MppDependencyModifier.addDependencies(nativeModule, it) }
        ctx.getUpdates()
            .takeIf { it.isNotEmpty() }
            ?.let { MppDependencyModifier.updateDependencies(nativeModule, it) }
    }

    context(EditModuleContext)
    override fun addRepository(repository: ApiRepository) {
        validateRepositoryType(repository)
        kmpData.modifier.addRepository(
            module = nativeModule,
            repository = repository.toUnifiedRepository()
        )
    }

    context(EditModuleContext)
    override fun removeRepository(repository: PackageSearchDeclaredRepository) {
        validateContextType()
        validateRepositoryType(repository)
        kmpData.modifier.deleteRepository(
            module = nativeModule,
            repository = repository.toUnifiedRepository()
        )
    }

    override val compatiblePackageTypes: List<PackagesType>
        get() = variants.commonMain.compatiblePackageTypes

    override val mainVariantName: String
        get() = "commonMain"

}
