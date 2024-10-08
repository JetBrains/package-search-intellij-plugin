@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.module.Module
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoProvider
import com.jetbrains.packagesearch.plugin.core.PackageSearch
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.gradle.utils.toGradle
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.packagesearch.api.v3.http.PackageSearchEndpointPaths.knownRepositories

class KotlinMultiplatformModuleProvider : AbstractGradleModuleProvider() {

    override suspend fun FlowCollector<PackageSearchModule?>.transform(
        context: PackageSearchModuleBuilderContext,
        module: Module,
        model: PackageSearchGradleModel,
    ) {
        if (PackageSearch.isKMPEnabled && model.isKotlinMultiplatformApplied && !model.isAmperApplied)
            MppCompilationInfoProvider.sourceSetsMap(context.project, model.projectDir)
                .collect { compilationModel ->
                    val variants = module.getKMPVariants(
                        context = context,
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
                            projectDir = model.projectDir,
                        ),
                        buildFilePath = model.buildFilePath,
                        declaredRepositories = model.declaredRepositories.toGradle(context),
                        variants = variants,
                        packageSearchModel = model,
                        availableKnownRepositories = context.knownRepositories,
                        nativeModule = module
                    )
                    emit(pkgsModule)
                }

    }

}

