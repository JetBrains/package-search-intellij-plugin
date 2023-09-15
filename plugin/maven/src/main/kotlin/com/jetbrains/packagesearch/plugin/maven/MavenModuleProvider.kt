@file:Suppress("UnstableApiUsage", "CompanionObjectInExtension")

package com.jetbrains.packagesearch.plugin.maven

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.xml.XmlText
import com.jetbrains.packagesearch.plugin.core.data.IconProvider.Icons
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleProvider
import com.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.asMavenApiPackage
import com.jetbrains.packagesearch.plugin.core.utils.filesChangedEventFlow
import com.jetbrains.packagesearch.plugin.core.utils.getIcon
import com.jetbrains.packagesearch.plugin.core.utils.mapUnit
import com.jetbrains.packagesearch.plugin.core.utils.registryFlow
import com.jetbrains.packagesearch.plugin.core.utils.watchExternalFileChanges
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.javaApi
import org.jetbrains.packagesearch.api.v3.search.javaRuntime
import org.jetbrains.packagesearch.maven.ProjectObjectModel
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import java.io.File
import com.intellij.openapi.module.Module as NativeModule

class MavenModuleProvider : PackageSearchModuleProvider {

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
            IntelliJApplication.registryFlow("org.jetbrains.packagesearch.sonatype",).mapUnit()
        )

        private fun buildMavenParentHierarchy(pomFile: File): String {
            val pom = XML.decodeFromString<ProjectObjectModel>(pomFile.readText())
            val parentFile = pom.parent?.relativePath
                ?.let { pomFile.parentFile.resolve(it) }
                ?: return ":"
            val projectName = pom.name ?: pom.artifactId ?: pomFile.parentFile.name
            val parentHierarchy = buildMavenParentHierarchy(parentFile)
            return parentHierarchy.suffixIfNot(":") + projectName
        }

        suspend fun Module.toPackageSearch(
            context: PackageSearchModuleBuilderContext,
            mavenProject: MavenProject,
        ): PackageSearchMavenModule {
            val declaredDependencies = getDeclaredDependencies(context)
            return PackageSearchMavenModule(
                name = mavenProject.name ?: name,
                identity = PackageSearchModule.Identity(
                    group = "maven",
                    path = buildMavenParentHierarchy(mavenProject.file.asRegularFile())
                ),
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
                MavenProjectsManager.getInstance(context.project)
                    .findProject(this@getDeclaredDependencies)
                    ?.file
                    ?.let { MavenDomUtil.getMavenDomProjectModel(context.project, it) }
                    ?.dependencies
                    ?.dependencies
                    ?.mapNotNull {
                        MavenDependencyModel(
                            groupId = it.groupId.stringValue ?: return@mapNotNull null,
                            artifactId = it.artifactId.stringValue ?: return@mapNotNull null,
                            version = it.version.stringValue,
                            scope = it.scope.stringValue,
                            indexes = DependencyDeclarationIndexes(
                                declarationStartIndex = it.xmlElement?.textOffset ?: return@mapNotNull null,
                                versionStartIndex = it.version.xmlTag?.children
                                    ?.firstOrNull { it is XmlText }
                                    ?.textOffset
                            )
                        )
                    }
                    ?: emptyList()
            }.distinct()

            val distinctIds = declaredDependencies
                .asSequence()
                .map { it.packageId }
                .distinct()

            val isSonatype = Registry.`is`("org.jetbrains.packagesearch.sonatype")

            val remoteInfo =
                if (isSonatype) {
                    context.getPackageInfoByIds(distinctIds.toSet())
                } else {
                    context.getPackageInfoByIdHashes(distinctIds.map { ApiPackage.hashPackageId(it) }.toSet())
                }

            return declaredDependencies
                .associateBy { it.packageId }
                .mapNotNull { (packageId, declaredDependency) ->
                    PackageSearchDeclaredBaseMavenPackage(
                        id = packageId,
                        declaredVersion = NormalizedVersion.from(declaredDependency.version),
                        latestStableVersion = remoteInfo[packageId]?.versions?.latestStable?.normalized
                            ?: NormalizedVersion.Missing,
                        latestVersion = remoteInfo[packageId]?.versions?.latest?.normalized
                            ?: NormalizedVersion.Missing,
                        remoteInfo = remoteInfo[packageId]?.asMavenApiPackage(),
                        groupId = declaredDependency.groupId,
                        artifactId = declaredDependency.artifactId,
                        scope = declaredDependency.scope,
                        declarationIndexes = declaredDependency.indexes,
                        icon = remoteInfo[packageId]?.getIcon(declaredDependency.version) ?: Icons.MAVEN
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
    ): Flow<PackageSearchModuleData?> = flow {
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



