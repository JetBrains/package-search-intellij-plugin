@file:Suppress("UnstableApiUsage", "CompanionObjectInExtension")

package org.jetbrains.packagesearch.plugin.maven

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.toNioPath
import kotlinx.coroutines.flow.*
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.buildPackagesType
import org.jetbrains.packagesearch.api.v3.search.javaApi
import org.jetbrains.packagesearch.api.v3.search.javaRuntime
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleTransformer
import org.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import org.jetbrains.packagesearch.plugin.core.utils.filesChangedEventFlow
import org.jetbrains.packagesearch.plugin.core.utils.mapUnit
import org.jetbrains.packagesearch.plugin.core.utils.packageId
import org.jetbrains.packagesearch.plugin.core.utils.watchExternalFileChanges
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

    override fun PolymorphicModuleBuilder<PackageSearchDeclaredPackage>.registerVersionSerializer() {
        subclass(PackageSearchDeclaredMavenPackage.serializer())
    }

    private fun getModuleChangesFlow(context: ProjectContext, pomPath: String): Flow<Unit> = merge(
        watchExternalFileChanges(mavenSettingsFilePath),
        context.project.filesChangedEventFlow
            .flatMapLatest { it.map { it.path }.asFlow() }
            .filter {
                val r = it == pomPath
                r
            }
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

    override fun buildModule(
        context: PackageSearchModuleBuilderContext,
        nativeModule: NativeModule,
    ): Flow<PackageSearchModule?> =
        flow {
            val mavenProject = context.project.findMavenProjectFor(nativeModule) ?: return@flow
            emit(nativeModule.toPackageSearch(context, mavenProject))
            getModuleChangesFlow(context, mavenProject.file.path)
                .collect { emit(nativeModule.toPackageSearch(context, mavenProject)) }
        }

    private suspend fun Module.getDeclaredDependencies(context: PackageSearchModuleBuilderContext): List<PackageSearchDeclaredPackage> {
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
                PackageSearchDeclaredMavenPackage(
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

}

