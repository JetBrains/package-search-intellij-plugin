@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.maven

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.writeAction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.data.PackageUpdate
import org.jetbrains.packagesearch.plugin.core.data.WithIcon.Icons
import org.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import org.jetbrains.packagesearch.plugin.core.utils.getNativeModule

data class MavenPackageUpdate(
    override val installedPackage: PackageSearchDeclaredMavenPackage,
    override val version: String?,
    val scope: String?
) : PackageUpdate

@Serializable
@SerialName("maven")
data class PackageSearchMavenModule(
    override val name: String,
    override val projectDirPath: String,
    override val buildFilePath: String,
    override val declaredKnownRepositories: Map<String, ApiRepository>,
    override val declaredDependencies: List<PackageSearchDeclaredPackage>,
    override val defaultScope: String? = null,
    override val compatiblePackageTypes: List<PackagesType>
) : PackageSearchModule.Base {

    override val icon
        get() = Icons.MAVEN

    override suspend fun updateDependencies(
        context: ProjectContext,
        updateCandidates: List<PackageUpdate>,
        knownRepositories: List<ApiRepository>
    ) {
        updateCandidates.asSequence()
            .filterIsInstance<MavenPackageUpdate>()
            .filter { it.scope != null || it.version != null }
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
                        .updateDependency(getNativeModule(context), oldDescriptor, newDescriptor)
                }
            }
    }

    override suspend fun installDependency(
        context: ProjectContext,
        apiPackage: ApiPackage,
        selectedVersion: String,
        selectedScope: String?
    ) {
        val mavenApiPackage = apiPackage as? ApiMavenPackage ?: return

        val descriptor = UnifiedDependency(
            groupId = mavenApiPackage.groupId,
            artifactId = mavenApiPackage.artifactId,
            version = selectedVersion,
            configuration = selectedScope
        )
        writeAction {
            DependencyModifierService.getInstance(context.project)
                .addDependency(getNativeModule(context), descriptor)
        }
    }

    override suspend fun removeDependency(
        context: ProjectContext,
        installedPackage: PackageSearchDeclaredPackage
    ) {
        val mavenPackage =
            installedPackage as? PackageSearchDeclaredMavenPackage ?: return

        val descriptor = UnifiedDependency(
            groupId = mavenPackage.groupId,
            artifactId = mavenPackage.artifactId,
            version = mavenPackage.declaredVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName,
            configuration = mavenPackage.scope
        )

        writeAction {
            DependencyModifierService.getInstance(context.project)
                .removeDependency(getNativeModule(context), descriptor)
        }
    }
}