@file:Suppress("UnstableApiUsage", "CompanionObjectInExtension")

package com.jetbrains.packagesearch.plugin.maven

import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleProvider
import com.jetbrains.packagesearch.plugin.core.utils.smartModeFlow
import java.nio.file.Paths
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.intellij.openapi.module.Module as NativeModule

class MavenModuleProvider : PackageSearchModuleProvider {

    override fun provideModule(
        context: PackageSearchModuleBuilderContext,
        nativeModule: NativeModule,
    ): Flow<PackageSearchModuleData?> = nativeModule.project
        .smartModeFlow
        .flatMapLatest {
            when (val mavenProject = context.project.findMavenProjectFor(nativeModule)) {
                null -> emptyFlow()
                else -> getModuleChangesFlow(context, Paths.get(mavenProject.file.path))
                    .map { nativeModule.toPackageSearch(context, mavenProject) }
            }
        }
        .map {
            PackageSearchModuleData(
                module = it,
                dependencyManager = PackageSearchMavenDependencyManager(nativeModule)
            )
        }
}



