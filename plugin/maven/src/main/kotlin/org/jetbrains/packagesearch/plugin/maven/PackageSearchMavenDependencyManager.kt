@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.maven

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

class PackageSearchMavenDependencyManager(
    private val nativeModule: Module
) : PackageSearchDependencyManager {
    override suspend fun updateDependencies(
        context: ProjectContext,
        data: List<UpdatePackageData>,
        knownRepositories: List<ApiRepository>
    ) {
        data.asSequence()
            .filterIsInstance<MavenUpdatePackageData>()
            .filter { it.newScope != null || it.newVersion != null }
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
                    configuration = scope ?: installedPackage.scope
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
        val mavenData = data as? MavenInstallPackageData ?: return

        val descriptor = UnifiedDependency(
            groupId = mavenData.apiPackage.groupId,
            artifactId = mavenData.apiPackage.artifactId,
            version = data.selectedVersion,
            configuration = data.selectedScope
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
        val mavenData =
            data as? MavenRemovePackageData ?: return

        val descriptor = UnifiedDependency(
            groupId = mavenData.declaredPackage.groupId,
            artifactId = mavenData.declaredPackage.artifactId,
            version = mavenData.declaredPackage.declaredVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName,
            configuration = mavenData.declaredPackage.scope
        )

        writeAction {
            DependencyModifierService.getInstance(context.project)
                .removeDependency(nativeModule, descriptor)
        }
    }
}