@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.module.Module
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoProvider
import com.jetbrains.packagesearch.plugin.core.PackageSearch
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.utils.toDirectory
import com.jetbrains.packagesearch.plugin.gradle.utils.toGradle
import kotlinx.coroutines.flow.FlowCollector

class KotlinMultiplatformModuleProvider : AbstractGradleModuleProvider() {

    context(PackageSearchModuleBuilderContext)
    override suspend fun FlowCollector<PackageSearchModule?>.transform(
        module: Module,
        model: PackageSearchGradleModel,
    ) {
        if (PackageSearch.isKMPEnabled && model.isKotlinMultiplatformApplied && !model.isAmperApplied)
            MppCompilationInfoProvider.sourceSetsMap(project, model.projectDir)
                .collect { compilationModel ->
                    val variants = module.getKMPVariants(
                        compilationModel = compilationModel,
                        buildFilePath = model.buildFilePath,
                        availableScopes = model.configurations
                            .filter { it.canBeDeclared }
                            .map { it.name }
                    ).associateBy { it.name }
                    val pkgsModule = PackageSearchKotlinMultiplatformModule(
                        name = model.projectName,
                        identity = PackageSearchModule.Identity(
                            group = "gradle",
                            path = model.projectIdentityPath,
                            projectDir = model.projectDir.toDirectory(),
                        ),
                        buildFilePath = model.buildFilePath,
                        declaredRepositories = model.declaredRepositories.toGradle(),
                        variants = variants,
                        packageSearchModel = model,
                        availableKnownRepositories = knownRepositories,
                        nativeModule = module
                    )
                    emit(pkgsModule)
                }

    }

}

