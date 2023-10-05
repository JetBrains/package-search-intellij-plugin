@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.jetbrains.packagesearch.plugin.core.data.InstallPackageData
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.RemovePackageData
import com.jetbrains.packagesearch.plugin.core.data.UpdatePackageData
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchKnownRepositoriesContext
import com.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import java.nio.file.Paths
import kotlin.io.path.writeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.configuration.GRADLE_SYSTEM_ID
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

class PackageSearchGradleDependencyManager(
    private val gradleModel: PackageSearchGradleModel,
    private val nativeModule: Module,
) : PackageSearchDependencyManager {

    suspend fun updateGradleDependencies(
        context: PackageSearchKnownRepositoriesContext,
        data: List<GradleUpdatePackageData>,
    ) {
        val updates = data.filter { it.newVersion != null || it.newScope != it.installedPackage.scope }
            .map { (installedPackage, version, scope) ->
                val oldDescriptor = UnifiedDependency(
                    groupId = installedPackage.groupId,
                    artifactId = installedPackage.artifactId,
                    version = installedPackage.declaredVersion.versionName,
                    configuration = installedPackage.scope
                )
                val newDescriptor = UnifiedDependency(
                    groupId = installedPackage.groupId,
                    artifactId = installedPackage.artifactId,
                    version = version ?: installedPackage.declaredVersion.versionName,
                    configuration = scope
                )
                oldDescriptor to newDescriptor
            }
        writeAction {
            updates.forEach { (oldDescriptor, newDescriptor) ->
                DependencyModifierService.getInstance(context.project)
                    .updateDependency(nativeModule, oldDescriptor, newDescriptor)
            }
        }

    }

    override suspend fun updateDependencies(
        context: PackageSearchKnownRepositoriesContext,
        data: List<UpdatePackageData>,
    ) = updateGradleDependencies(
        context = context,
        data = data.filterIsInstance<GradleUpdatePackageData>(),
    )

    override suspend fun addDependency(
        context: PackageSearchKnownRepositoriesContext,
        data: InstallPackageData,
    ) {
        val gradleData = data as? GradleInstallPackageData ?: return
        installGradleDependencies(context, gradleData)
    }

    suspend fun installGradleDependencies(
        context: ProjectContext,
        data: GradleInstallPackageData,
    ) {
        val descriptor = UnifiedDependency(
            groupId = data.apiPackage.groupId,
            artifactId = data.apiPackage.artifactId,
            version = data.selectedVersion,
            configuration = data.selectedConfiguration
        )
        if (gradleModel.buildFilePath == null) {
            withContext(Dispatchers.IO) {
                gradleModel.projectDir.resolve("build.gradle.kts")
                    .writeText(
                        """
                            dependencies {
                                ${descriptor.toGradleDependencyString()}
                            }
                        """.trimIndent()
                    )
            }
            nativeModule.project.basePath?.let {
                ExternalSystemUtil.refreshProject(
                    /* project = */ nativeModule.project,
                    /* externalSystemId = */ GRADLE_SYSTEM_ID,
                    /* externalProjectPath = */ it,
                    /* isPreviewMode = */ false,
                    /* progressExecutionMode = */ ProgressExecutionMode.IN_BACKGROUND_ASYNC
                )
            }

        } else {
            writeAction {
                DependencyModifierService.getInstance(context.project)
                    .addDependency(nativeModule, descriptor)
            }
        }
    }

    override suspend fun removeDependency(
        context: ProjectContext,
        data: RemovePackageData,
    ) {
        val gradleData = data as? GradleRemovePackageData ?: return
        removeGradleDependencies(context, gradleData)
    }

    suspend fun removeGradleDependencies(
        context: ProjectContext,
        data: GradleRemovePackageData,
    ) {
        val descriptor = UnifiedDependency(
            groupId = data.declaredPackage.groupId,
            artifactId = data.declaredPackage.artifactId,
            version = data.declaredPackage.declaredVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName,
            configuration = data.declaredPackage.scope
        )

        writeAction {
            DependencyModifierService.getInstance(context.project)
                .removeDependency(nativeModule, descriptor)
        }
    }
}

private fun UnifiedDependency.toGradleDependencyString() =
    """$scope("${coordinates.groupId}:${coordinates.artifactId}:${coordinates.version}")"""
