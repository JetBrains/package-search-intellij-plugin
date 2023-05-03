@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.maven

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.toNioPath
import kotlinx.coroutines.flow.*
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.data.WithIcon.PathSourceType.ClasspathResources
import org.jetbrains.packagesearch.plugin.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.extensions.PackageSearchModuleTransformer
import org.jetbrains.packagesearch.plugin.extensions.ProjectContext
import org.jetbrains.packagesearch.plugin.utils.filesChangedEventFlow
import org.jetbrains.packagesearch.plugin.utils.mapUnit
import org.jetbrains.packagesearch.plugin.utils.nativeModule
import org.jetbrains.packagesearch.plugin.utils.watchFileChanges
import com.intellij.openapi.module.Module as NativeModule


class PackageSearchMavenModuleTransformer : PackageSearchModuleTransformer {

    companion object {
        val mavenSettingsFilePath
            get() = System.getenv("M2_HOME")
                ?.let { "$it/conf/settings.xml".toNioPath() }
                ?: System.getProperty("user.home").plus("/.m2/settings.xml").toNioPath()
    }

    override val moduleSerializer
        get() = PackageSearchMavenModule.serializer()

    override val versionSerializer
        get() = PackageSearchDeclaredMavenDependency.serializer()

    context(ProjectContext)
    private fun getModuleChangesFlow(pomPath: String): Flow<Unit> = merge(
        watchFileChanges(mavenSettingsFilePath),
        project.filesChangedEventFlow
            .filter { it.any { it.path == pomPath } }
            .mapUnit()
    )

    context(PackageSearchModuleBuilderContext)
    private suspend fun Module.toPackageSearch(mavenProject: MavenProject) =
        PackageSearchMavenModule(
            name = mavenProject.name ?: name,
            projectDirPath = mavenProject.path,
            buildFilePath = mavenProject.file.path,
            declaredKnownRepositories = getDeclaredKnownRepositories(),
            declaredDependencies = getDeclaredDependencies()
        )

    context(PackageSearchModuleBuilderContext)
    override fun buildModule(nativeModule: NativeModule): Flow<PackageSearchModule?> =
        flow {
            val mavenProject = project.findMavenProjectFor(nativeModule)
            if (mavenProject == null) {
                emit(null)
                return@flow
            }
            emit(nativeModule.toPackageSearch(mavenProject))
            getModuleChangesFlow(mavenProject.file.path)
                .collect { emit(nativeModule.toPackageSearch(mavenProject)) }
        }

    context(PackageSearchModuleBuilderContext)
    private suspend fun Module.getDeclaredDependencies(): List<PackageSearchDeclaredDependency> {
        val declaredDependencies = readAction {
            DependencyModifierService.getInstance(project)
                .declaredDependencies(this)
        }

        val remoteInfo = declaredDependencies
            .asSequence()
            .filter { it.coordinates.artifactId != null && it.coordinates.groupId != null }
            .map { it.packageId }
            .distinct()
            .map { ApiPackage.hashPackageId(it) }
            .toSet()
            .let { getPackageInfoByIdHashes(it) }

        return declaredDependencies
            .associateBy { it.packageId }
            .mapNotNull { (packageId, declaredDependency) ->
                PackageSearchDeclaredMavenDependency(
                    id = packageId,
                    declaredVersion = NormalizedVersion.from(declaredDependency.coordinates.version),
                    latestStableVersion = remoteInfo[packageId]
                        ?.versions
                        ?.latest
                        ?.normalized
                        ?.takeIf { it.isStable }
                        ?: remoteInfo[packageId]
                            ?.versions
                            ?.all
                            ?.find { it.normalized.isStable }
                            ?.normalized
                        ?: NormalizedVersion.Missing,
                    latestVersion = remoteInfo[packageId]?.versions?.latest?.normalized ?: NormalizedVersion.Missing,
                    remoteInfo = remoteInfo[packageId],
                    groupId = declaredDependency.coordinates.groupId ?: return@mapNotNull null,
                    artifactId = declaredDependency.coordinates.artifactId ?: return@mapNotNull null,
                    scope = declaredDependency.unifiedDependency.scope,
                    declarationIndexes = declaredDependency.evaluateDeclaredIndexes(),
                )
            }
    }

    context(PackageSearchModuleBuilderContext)
    private suspend fun NativeModule.getDeclaredKnownRepositories(): Map<String, ApiRepository> {
        val declaredDependencies = readAction {
            DependencyModifierService.getInstance(project)
                .declaredRepositories(this)
        }
            .mapNotNull { it.id }
        return knownRepositories.filterKeys { it in declaredDependencies }
    }

    context(ProjectContext)
    override suspend fun updateDependencies(
        module: PackageSearchModule,
        installedPackages: List<PackageSearchDeclaredDependency>,
        knownRepositories: List<ApiRepository>,
    ) {
        val updates =
            installedPackages.filterIsInstance<PackageSearchDeclaredMavenDependency>()
                .filter { it.latestVersion != null && it.declaredVersion != it.latestVersion }
                .map {
                    val oldDescriptor = UnifiedDependency(
                        groupId = it.groupId,
                        artifactId = it.artifactId,
                        version = it.declaredVersion?.versionName,
                        configuration = it.scope
                    )
                    val newDescriptor = UnifiedDependency(
                        groupId = it.groupId,
                        artifactId = it.artifactId,
                        version = it.latestVersion?.versionName ?: it.declaredVersion?.versionName,
                        configuration = it.scope
                    )
                    oldDescriptor to newDescriptor
                }
        writeAction {
            updates.forEach { (oldDescriptor, newDescriptor) ->
                DependencyModifierService.getInstance(project)
                    .updateDependency(module.nativeModule, oldDescriptor, newDescriptor)
            }
        }
    }


    context(ProjectContext)
    override suspend fun installDependency(
        module: PackageSearchModule,
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
            DependencyModifierService.getInstance(project)
                .addDependency(module.nativeModule, descriptor)
        }
    }

    context(ProjectContext)
    override suspend fun removeDependency(
        module: PackageSearchModule,
        installedPackage: PackageSearchDeclaredDependency,
    ) {
        val mavenPackage =
            installedPackage as? PackageSearchDeclaredMavenDependency ?: return

        val descriptor = UnifiedDependency(
            groupId = mavenPackage.groupId,
            artifactId = mavenPackage.artifactId,
            version = mavenPackage.declaredVersion?.versionName,
            configuration = mavenPackage.scope
        )

        writeAction {
            DependencyModifierService.getInstance(project)
                .removeDependency(module.nativeModule, descriptor)
        }
    }
}

