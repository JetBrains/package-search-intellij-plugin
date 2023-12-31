@file:Suppress("UnstableApiUsage", "CompanionObjectInExtension")

package com.jetbrains.packagesearch.plugin.maven

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleProvider
import com.jetbrains.packagesearch.plugin.core.utils.smartModeFlow
import java.nio.file.Paths
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.intellij.openapi.module.Module as NativeModule

class MavenModuleProvider : PackageSearchModuleProvider {

    context(PackageSearchModuleBuilderContext)
    override fun provideModule(nativeModule: NativeModule, ): Flow<PackageSearchModule?> = nativeModule.project
        .smartModeFlow
        .flatMapLatest {
            when (val mavenProject = project.findMavenProjectFor(nativeModule)) {
                null -> emptyFlow()
                else -> getModuleChangesFlow(Paths.get(mavenProject.file.path))
                    .map { nativeModule.toPackageSearch(mavenProject) }
            }
        }
}



