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
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.data.WithIcon.Icons
import org.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import org.jetbrains.packagesearch.plugin.core.utils.getNativeModule

@Serializable
@SerialName("gradle")
data class PackageSearchGradleModule(
    override val name: String,
    override val projectDirPath: String,
    override val buildFilePath: String?,
    override val declaredKnownRepositories: Map<String, ApiRepository>,
    override val declaredDependencies: List<PackageSearchDeclaredDependency>,
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
        installedPackages: List<PackageSearchDeclaredDependency>,
        knownRepositories: List<ApiRepository>,
        onlyStable: Boolean,
    ) {
        val updates = installedPackages
            .filterIsInstance<PackageSearchDeclaredGradleDependency>()
            .filter { it.declaredVersion < if (onlyStable) it.latestStableVersion else it.latestVersion }
            .map {
                val oldDescriptor = UnifiedDependency(
                    groupId = it.module,
                    artifactId = it.name,
                    version = it.declaredVersion.versionName,
                    configuration = it.configuration
                )
                val newDescriptor = UnifiedDependency(
                    groupId = it.module,
                    artifactId = it.name,
                    version = if (onlyStable) it.latestStableVersion.versionName else it.latestVersion.versionName,
                    configuration = it.configuration
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
        selectedVersion: String,
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
        installedPackage: PackageSearchDeclaredDependency,
    ) {
        val gradleDependency = installedPackage as? PackageSearchDeclaredGradleDependency ?: return

        val descriptor = UnifiedDependency(
            groupId = gradleDependency.module,
            artifactId = gradleDependency.name,
            version = gradleDependency.declaredVersion.versionName,
            configuration = gradleDependency.configuration
        )

        writeAction {
            DependencyModifierService.getInstance(context.project)
                .removeDependency(getNativeModule(context), descriptor)
        }
    }
}
