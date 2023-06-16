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
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.data.WithIcon.Icons
import org.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import org.jetbrains.packagesearch.plugin.core.utils.getNativeModule

@Serializable
@SerialName("maven")
data class PackageSearchMavenModule(
    override val name: String,
    override val projectDirPath: String,
    override val buildFilePath: String,
    override val declaredKnownRepositories: Map<String, ApiRepository>,
    override val declaredDependencies: List<PackageSearchDeclaredDependency>,
    override val defaultScope: String? = null,
    override val compatiblePackageTypes: List<PackagesType>
) : PackageSearchModule.Base {

    override val icon
        get() = Icons.MAVEN

    override suspend fun updateDependencies(
        context: ProjectContext,
        installedPackages: List<PackageSearchDeclaredDependency>,
        knownRepositories: List<ApiRepository>,
        onlyStable: Boolean
    ) {
        val updates =
            installedPackages.filterIsInstance<PackageSearchDeclaredMavenDependency>()
                .filter { it.declaredVersion < if (onlyStable) it.latestStableVersion else it.latestVersion }
                .map {
                    val oldDescriptor = UnifiedDependency(
                        groupId = it.groupId,
                        artifactId = it.artifactId,
                        version = it.declaredVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName,
                        configuration = it.scope
                    )
                    val newDescriptor = UnifiedDependency(
                        groupId = it.groupId,
                        artifactId = it.artifactId,
                        version = when {
                            onlyStable -> it.latestStableVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName
                            else -> it.latestVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName
                        },
                        configuration = it.scope
                    )
                    oldDescriptor to newDescriptor
                }
        updates.forEach { (oldDescriptor, newDescriptor) ->
            writeAction {
                DependencyModifierService.getInstance(context.project)
                    .updateDependency(getNativeModule(context), oldDescriptor, newDescriptor)
            }
        }
    }

    override suspend fun installDependency(
        context: ProjectContext,
        apiPackage: ApiPackage,
        selectedVersion: String
    ) {
        val mavenApiPackage = apiPackage as? ApiMavenPackage ?: return

        val descriptor = UnifiedDependency(
            groupId = mavenApiPackage.groupId,
            artifactId = mavenApiPackage.artifactId,
            version = selectedVersion,
            configuration = null
        )
        writeAction {
            DependencyModifierService.getInstance(context.project)
                .addDependency(getNativeModule(context), descriptor)
        }
    }

    override suspend fun removeDependency(
        context: ProjectContext,
        installedPackage: PackageSearchDeclaredDependency
    ) {
        val mavenPackage =
            installedPackage as? PackageSearchDeclaredMavenDependency ?: return

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