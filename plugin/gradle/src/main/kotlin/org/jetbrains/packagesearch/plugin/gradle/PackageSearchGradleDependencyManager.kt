@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.data.InstallPackageData
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import org.jetbrains.packagesearch.plugin.core.data.RemovePackageData
import org.jetbrains.packagesearch.plugin.core.data.UpdatePackageData
import org.jetbrains.packagesearch.plugin.core.extensions.ProjectContext

class PackageSearchGradleDependencyManager(
    private val nativeModule: Module
) : PackageSearchDependencyManager {

    suspend fun updateDependencies(
        context: ProjectContext,
        gradleData: List<GradleUpdatePackageData>,
        knownRepositories: List<ApiRepository>
    ) {
        val updates = gradleData.filter { it.newVersion != null || it.newConfiguration != it.installedPackage.scope }
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
        context: ProjectContext,
        data: List<UpdatePackageData>,
        knownRepositories: List<ApiRepository>
    ) = updateDependencies(
        context = context,
        gradleData = data.filterIsInstance<GradleUpdatePackageData>(),
        knownRepositories = knownRepositories
    )

    override suspend fun installDependency(
        context: ProjectContext,
        data: InstallPackageData
    ) {
        val gradleData = data as? GradleInstallPackageData ?: return
        installDependency(gradleData, data, context)
    }

    suspend fun installDependency(
        gradleData: GradleInstallPackageData,
        data: GradleInstallPackageData,
        context: ProjectContext
    ) {
        val descriptor = UnifiedDependency(
            groupId = gradleData.apiPackage.groupId,
            artifactId = gradleData.apiPackage.artifactId,
            version = data.selectedVersion,
            configuration = data.selectedConfiguration
        )
        writeAction {
            DependencyModifierService.getInstance(context.project)
                .addDependency(nativeModule, descriptor)
        }
    }

    override suspend fun removeDependency(
        context: ProjectContext,
        data: RemovePackageData
    ) {
        val gradleData = data as? GradleRemovePackageData ?: return
        removeDependency(gradleData, context)
    }

    suspend fun removeDependency(
        gradleData: GradleRemovePackageData,
        context: ProjectContext
    ) {
        val descriptor = UnifiedDependency(
            groupId = gradleData.declaredPackage.groupId,
            artifactId = gradleData.declaredPackage.artifactId,
            version = gradleData.declaredPackage.declaredVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName,
            configuration = gradleData.declaredPackage.scope
        )

        writeAction {
            DependencyModifierService.getInstance(context.project)
                .removeDependency(nativeModule, descriptor)
        }
    }
}