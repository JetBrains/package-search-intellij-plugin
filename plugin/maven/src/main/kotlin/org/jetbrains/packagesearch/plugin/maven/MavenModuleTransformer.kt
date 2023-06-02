@file:Suppress("UnstableApiUsage", "UNNECESSARY_SAFE_CALL")

package org.jetbrains.packagesearch.plugin.maven

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.toNioPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.buildPackagesType
import org.jetbrains.packagesearch.api.v3.search.javaApi
import org.jetbrains.packagesearch.api.v3.search.javaRuntime
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleTransformer
import org.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import org.jetbrains.packagesearch.plugin.core.utils.*
import com.intellij.openapi.module.Module as NativeModule


class MavenModuleTransformer : PackageSearchModuleTransformer {

    companion object {
        val mavenSettingsFilePath
            get() = System.getenv("M2_HOME")
                ?.let { "$it/conf/settings.xml".toNioPath() }
                ?: System.getProperty("user.home").plus("/.m2/settings.xml").toNioPath()
    }

    override fun PolymorphicModuleBuilder<PackageSearchModule>.registerModuleSerializer() {
        subclass(PackageSearchMavenModule.serializer())
    }

    override fun PolymorphicModuleBuilder<PackageSearchDeclaredDependency>.registerVersionSerializer() {
        subclass(PackageSearchDeclaredMavenDependency.serializer())
    }

    private fun getModuleChangesFlow(context: ProjectContext, pomPath: String): Flow<Unit> = merge(
        watchFileChanges(mavenSettingsFilePath),
        context.project.filesChangedEventFlow
            .filter { it.any { it.path == pomPath } }
            .mapUnit()
    )

    private suspend fun Module.toPackageSearch(context: PackageSearchModuleBuilderContext, mavenProject: MavenProject) =
        PackageSearchMavenModule(
            name = mavenProject.name ?: name,
            projectDirPath = mavenProject.path.removeSuffix("/pom.xml"),
            buildFilePath = mavenProject.file.path,
            declaredKnownRepositories = getDeclaredKnownRepositories(context),
            declaredDependencies = getDeclaredDependencies(context),
            compatiblePackageTypes = buildPackagesType {
                mavenPackages()
                gradlePackages {
                    mustBeRootPublication = false
                    variant {
                        mustHaveFilesAttribute = true
                        javaApi()
                    }
                    variant {
                        mustHaveFilesAttribute = false
                        javaRuntime()
                    }
                }
            },
        )

    override fun buildModule(context: PackageSearchModuleBuilderContext, nativeModule: NativeModule): Flow<PackageSearchModule?> =
        flow {
            val mavenProject = context.project.findMavenProjectFor(nativeModule)
            if (mavenProject == null) {
                emit(null)
                return@flow
            }
            emit(nativeModule.toPackageSearch(context, mavenProject))
            getModuleChangesFlow(context, mavenProject.file.path)
                .collect { emit(nativeModule.toPackageSearch(context, mavenProject)) }
        }

    private suspend fun Module.getDeclaredDependencies(context: PackageSearchModuleBuilderContext): List<PackageSearchDeclaredDependency> {
        val declaredDependencies = readAction {
            DependencyModifierService.getInstance(context.project)
                .declaredDependencies(this)
        }

        val remoteInfo = declaredDependencies
            .asSequence()
            .filter { it.coordinates.artifactId != null && it.coordinates.groupId != null }
            .map { it.packageId }
            .distinct()
            .map { ApiPackage.hashPackageId(it) }
            .toSet()
            .let { context.getPackageInfoByIdHashes(it) }

        return declaredDependencies
            .associateBy { it.packageId }
            .mapNotNull { (packageId, declaredDependency) ->
                PackageSearchDeclaredMavenDependency(
                    id = packageId,
                    declaredVersion = NormalizedVersion.from(declaredDependency.coordinates.version),
                    latestStableVersion = remoteInfo[packageId]?.versions
                        ?.asSequence()
                        ?.map { it.normalized }
                        ?.filter { it.isStable }
                        ?.maxOrNull()
                        ?: NormalizedVersion.Missing,
                    latestVersion = remoteInfo[packageId]?.versions
                        ?.asSequence()
                        ?.map { it.normalized }
                        ?.maxOrNull()
                        ?: NormalizedVersion.Missing,
                    remoteInfo = remoteInfo[packageId],
                    groupId = declaredDependency.coordinates.groupId ?: return@mapNotNull null,
                    artifactId = declaredDependency.coordinates.artifactId ?: return@mapNotNull null,
                    scope = declaredDependency.unifiedDependency.scope,
                    declarationIndexes = declaredDependency.evaluateDeclaredIndexes(),
                )
            }
    }

    private suspend fun NativeModule.getDeclaredKnownRepositories(context: PackageSearchModuleBuilderContext): Map<String, ApiRepository> {
        val declaredDependencies = readAction {
            DependencyModifierService.getInstance(project)
                .declaredRepositories(this)
        }
            .mapNotNull { it.id }
        return context.knownRepositories.filterKeys { it in declaredDependencies }
    }

    override suspend fun updateDependencies(
        context: ProjectContext,
        module: PackageSearchModule,
        installedPackages: List<PackageSearchDeclaredDependency>,
        knownRepositories: List<ApiRepository>,
        onlyStable: Boolean,
    ) {
        val updates =
            installedPackages.filterIsInstance<PackageSearchDeclaredMavenDependency>()
                .filter { it.declaredVersion < if (onlyStable) it.latestStableVersion else it.latestVersion }
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
                        version = if (onlyStable) it.latestStableVersion.versionName else it.latestVersion.versionName,
                        configuration = it.scope
                    )
                    oldDescriptor to newDescriptor
                }
        updates.forEach { (oldDescriptor, newDescriptor) ->
            writeAction {
                DependencyModifierService.getInstance(context.project)
                    .updateDependency(module.getNativeModule(context), oldDescriptor, newDescriptor)
            }
        }
    }


    override suspend fun installDependency(
        context: ProjectContext,
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
            DependencyModifierService.getInstance(context.project)
                .addDependency(module.getNativeModule(context), descriptor)
        }
    }

    override suspend fun removeDependency(
        context: ProjectContext,
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
            DependencyModifierService.getInstance(context.project)
                .removeDependency(module.getNativeModule(context), descriptor)
        }
    }
}

