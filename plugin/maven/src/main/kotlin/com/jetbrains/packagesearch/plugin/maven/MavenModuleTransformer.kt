@file:Suppress("UnstableApiUsage", "CompanionObjectInExtension")

package com.jetbrains.packagesearch.plugin.maven

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleProvider
import com.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.asMavenApiPackage
import com.jetbrains.packagesearch.plugin.core.utils.filesChangedEventFlow
import com.jetbrains.packagesearch.plugin.core.utils.mapUnit
import com.jetbrains.packagesearch.plugin.core.utils.packageId
import com.jetbrains.packagesearch.plugin.core.utils.registryStateFlow
import com.jetbrains.packagesearch.plugin.core.utils.watchExternalFileChanges
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.javaApi
import org.jetbrains.packagesearch.api.v3.search.javaRuntime
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import com.intellij.openapi.module.Module as NativeModule


class MavenModuleTransformer : PackageSearchModuleProvider {

    companion object {
        val mavenSettingsFilePath
            get() = System.getenv("M2_HOME")
                ?.let { "$it/conf/settings.xml".toNioPath() }
                ?: System.getProperty("user.home").plus("/.m2/settings.xml").toNioPath()

        val commonScopes = listOf("compile", "provided", "runtime", "test", "system", "import")


        fun getModuleChangesFlow(context: ProjectContext, pomPath: String): Flow<Unit> = merge(
            watchExternalFileChanges(mavenSettingsFilePath),
            context.project.filesChangedEventFlow
                .flatMapLatest { it.map { it.path }.asFlow() }
                .filter { it == pomPath }
                .mapUnit(),
            IntelliJApplication.registryStateFlow(context.coroutineScope, "org.jetbrains.packagesearch.localhost", false)
                .mapUnit()
        )

        suspend fun Module.toPackageSearch(
            context: PackageSearchModuleBuilderContext,
            mavenProject: MavenProject
        ): PackageSearchMavenModule {
            val declaredDependencies = getDeclaredDependencies(context)
            return PackageSearchMavenModule(
                name = mavenProject.name ?: name,
                buildFilePath = mavenProject.file.path,
                declaredKnownRepositories = getDeclaredKnownRepositories(context),
                declaredDependencies = declaredDependencies,
                availableScopes = commonScopes.plus(declaredDependencies.mapNotNull { it.scope }).distinct(),
                compatiblePackageTypes = buildPackageTypes {
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
        }

        suspend fun Module.getDeclaredDependencies(context: PackageSearchModuleBuilderContext): List<PackageSearchDeclaredBaseMavenPackage> {
            val declaredDependencies = readAction {
                DependencyModifierService.getInstance(context.project)
                    .declaredDependencies(this)
            }
                .distinctBy { it.unifiedDependency }

            val distinctIds = declaredDependencies
                .asSequence()
                .filter { it.coordinates.artifactId != null && it.coordinates.groupId != null }
                .map { it.packageId }
                .distinct()

            val isLocalhost = Registry.`is`("org.jetbrains.packagesearch.localhost", false)
            val remoteInfo =
                if (!isLocalhost) {
                    context.getPackageInfoByIds(distinctIds.toSet())
                } else {
                    context.getPackageInfoByIdHashes(distinctIds.map { ApiPackage.hashPackageId(it) }.toSet())
                }

            return declaredDependencies
                .associateBy { it.packageId }
                .mapNotNull { (packageId, declaredDependency) ->
                    PackageSearchDeclaredBaseMavenPackage(
                        id = packageId,
                        declaredVersion = NormalizedVersion.from(declaredDependency.coordinates.version),
                        latestStableVersion = remoteInfo[packageId]?.versions?.latestStable?.normalized
                            ?: NormalizedVersion.Missing,
                        latestVersion = remoteInfo[packageId]?.versions?.latest?.normalized
                            ?: NormalizedVersion.Missing,
                        remoteInfo = remoteInfo[packageId]?.asMavenApiPackage(),
                        groupId = declaredDependency.coordinates.groupId ?: return@mapNotNull null,
                        artifactId = declaredDependency.coordinates.artifactId ?: return@mapNotNull null,
                        scope = declaredDependency.unifiedDependency.scope,
                        declarationIndexes = declaredDependency.evaluateDeclaredIndexes(),
                    )
                }
        }

        suspend fun NativeModule.getDeclaredKnownRepositories(context: PackageSearchModuleBuilderContext): Map<String, ApiRepository> {
            val declaredDependencies = readAction {
                DependencyModifierService.getInstance(project)
                    .declaredRepositories(this)
            }
                .mapNotNull { it.id }
            return context.knownRepositories.filterKeys { it in declaredDependencies }
        }
    }

    override fun provideModule(
        context: PackageSearchModuleBuilderContext,
        nativeModule: NativeModule,
    ): Flow<PackageSearchModuleData?> =
        flow {
            val mavenProject = context.project.findMavenProjectFor(nativeModule) ?: return@flow
            emit(nativeModule.toPackageSearch(context, mavenProject))
            getModuleChangesFlow(context, mavenProject.file.path)
                .collect { emit(nativeModule.toPackageSearch(context, mavenProject)) }
        }
            .map {
                PackageSearchModuleData(
                    module = it,
                    dependencyManager = PackageSearchMavenDependencyManager(nativeModule)
                )
            }
}

