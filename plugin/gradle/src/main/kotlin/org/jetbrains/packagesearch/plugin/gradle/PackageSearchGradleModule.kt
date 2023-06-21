@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

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

data class GradlePackageUpdate(
    override val installedPackage: PackageSearchDeclaredGradlePackage,
    override val version: String?,
    val configuration: String
) : PackageUpdate

@Serializable
@SerialName("gradle")
data class PackageSearchGradleModule(
    override val name: String,
    override val projectDirPath: String,
    override val buildFilePath: String?,
    override val declaredKnownRepositories: Map<String, ApiRepository>,
    override val declaredDependencies: List<PackageSearchDeclaredPackage>,
    override val defaultScope: String = "implementation",
    override val compatiblePackageTypes: List<PackagesType>,
    val packageSearchModel: PackageSearchGradleModel,
    val availableKnownRepositories: Map<String, ApiRepository>
) : PackageSearchModule.Base {
    override val icon
        get() = Icons.GRADLE

    val defaultConfiguration
        get() = defaultScope

    override suspend fun updateDependencies(
        context: ProjectContext,
        updateCandidates: List<PackageUpdate>,
        knownRepositories: List<ApiRepository>
    ) {
        updateCandidates.asSequence()
            .filterIsInstance<GradlePackageUpdate>()
            .filter { it.version != null || it.configuration != it.installedPackage.configuration }
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
        val gradlePackage =
            installedPackage as? PackageSearchDeclaredGradlePackage ?: return

        val descriptor = UnifiedDependency(
            groupId = gradlePackage.module,
            artifactId = gradlePackage.name,
            version = gradlePackage.declaredVersion.takeIf { it !is NormalizedVersion.Missing }?.versionName,
            configuration = gradlePackage.configuration
        )

        writeAction {
            DependencyModifierService.getInstance(context.project)
                .removeDependency(getNativeModule(context), descriptor)
        }
    }
}
