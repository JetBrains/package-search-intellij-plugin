@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.packageSearch.mppDependencyUpdater.MppDependencyModifier
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Android
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Js
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Jvm
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Native
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoProvider
import com.jetbrains.packagesearch.plugin.core.PackageSearch
import com.jetbrains.packagesearch.plugin.core.data.IconProvider.Icons
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.utils.icon
import com.jetbrains.packagesearch.plugin.core.utils.toDirectory
import com.jetbrains.packagesearch.plugin.gradle.utils.getDeclaredDependencies
import com.jetbrains.packagesearch.plugin.gradle.utils.toGradleDependencyModel
import java.nio.file.Path
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.kotlinMultiplatform
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

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
                        declaredKnownRepositories = knownRepositories - DependencyModifierService
                            .getInstance(project)
                            .declaredRepositories(module)
                            .mapNotNull { it.id }
                            .toSet(),
                        variants = variants,
                        packageSearchModel = model,
                        availableKnownRepositories = knownRepositories,
                        nativeModule = module
                    )
                    emit(pkgsModule)
                }

    }

}

