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
    private val packageSearchGradleModule: PackageSearchGradleModule,
    private val nativeModule: Module
) : PackageSearchDependencyManager {
    override suspend fun updateDependencies(
        context: ProjectContext,
        data: List<UpdatePackageData>,
        knownRepositories: List<ApiRepository>
    ) {
        data.asSequence()
            .filterIsInstance<GradleUpdatePackageData>()
            .filter { it.newVersion != null || it.configuration != it.installedPackage.configuration }
            .map { (installedPackage, version, scope) ->
                val oldDescriptor = UnifiedDependency(
                    groupId = installedPackage.module,
                    artifactId = installedPackage.name,
                    version = installedPackage.declaredVersion.versionName,
                    configuration = installedPackage.configuration
                )
                val newDescriptor = UnifiedDependency(
                    groupId = installedPackage.module,
                    artifactId = installedPackage.name,
                    version = version ?: installedPackage.declaredVersion.versionName,
                    configuration = scope
                )
                oldDescriptor to newDescriptor
            }
            .forEach { (oldDescriptor, newDescriptor) ->
                writeAction {
                    DependencyModifierService.getInstance(context.project)
                        .updateDependency(nativeModule, oldDescriptor, newDescriptor)
                }
            }
    }

    override suspend fun installDependency(
        context: ProjectContext,
        data: InstallPackageData
    ) {
        val gradleData = data as? GradleInstallPackageData ?: return

        val descriptor = UnifiedDependency(
            groupId = gradleData.apiPackage.groupId,
            artifactId = gradleData.apiPackage.artifactId,
            version = data.selectedVersion,
            configuration = data.configuration
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

        val descriptor = UnifiedDependency(
            groupId = gradleData.declaredPackage.module,
            artifactId = gradleData.declaredPackage.name,
            version = gradleData.declaredPackage.declaredVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName,
            configuration = gradleData.configuration
        )

        writeAction {
            DependencyModifierService.getInstance(context.project)
                .removeDependency(nativeModule, descriptor)
        }
    }
}