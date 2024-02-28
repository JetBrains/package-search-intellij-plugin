@file:Suppress("UnstableApiUsage", "CompanionObjectInExtension")

package com.jetbrains.packagesearch.plugin.maven

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.toNioPathOrNull
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleProvider
import com.jetbrains.packagesearch.plugin.core.utils.isSourceSet
import com.jetbrains.packagesearch.plugin.core.utils.smartModeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import com.intellij.openapi.module.Module as NativeModule

class MavenModuleProvider : PackageSearchModuleProvider {

    context(PackageSearchModuleBuilderContext)
    override fun provideModule(nativeModule: NativeModule): Flow<PackageSearchModule?> = when {
        nativeModule.isSourceSet -> emptyFlow()
        else -> project.smartModeFlow.take(1).flatMapLatest {
            when (val mavenProject = project.findMavenProjectFor(nativeModule)) {
                null -> emptyFlow()
                else -> when (val mavenProjectPath = mavenProject.file.toNioPathOrNull()) {
                    null -> emptyFlow()
                    else -> getModuleChangesFlow(mavenProjectPath)
                        .map { nativeModule.toPackageSearch(mavenProject) }
                }
            }
        }
    }

    override fun getSyncStateFlow(project: Project): Flow<Boolean> =
        project.service<MavenSyncStateService.State>().asStateFlow()
}

