@file:Suppress("UnstableApiUsage")
@file:OptIn(ExperimentalContracts::class)

package com.jetbrains.packagesearch.plugin.maven

import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.xml.XmlText
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredRepository
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import com.jetbrains.packagesearch.plugin.core.utils.asMavenApiPackage
import com.jetbrains.packagesearch.plugin.core.utils.filesChangedEventFlow
import com.jetbrains.packagesearch.plugin.core.utils.flow
import com.jetbrains.packagesearch.plugin.core.utils.icon
import com.jetbrains.packagesearch.plugin.core.utils.isSameFileAsSafe
import com.jetbrains.packagesearch.plugin.core.utils.mapUnit
import com.jetbrains.packagesearch.plugin.core.utils.smartModeFlow
import com.jetbrains.packagesearch.plugin.core.utils.watchExternalFileChanges
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dsl.MavenDependencyModificator
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.jvmMavenPackages
import org.jetbrains.packagesearch.maven.POM_XML_NAMESPACE
import org.jetbrains.packagesearch.maven.ProjectObjectModel
import org.jetbrains.packagesearch.maven.decodeFromString
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

suspend fun Project.findMavenProjectFor(module: Module): MavenProject? =
    MavenProjectsManager.getInstance(this).let { readAction { it.findProject(module) } }

fun VirtualFile.asRegularFile() = File(path)
fun String.suffixIfNot(s: String) = if (endsWith(s)) this else this + s

val mavenSettingsFilePath
    get() = System.getenv("M2_HOME")
        ?.let { Paths.get("$it/conf/settings.xml") }
        ?: Paths.get(System.getProperty("user.home").plus("/.m2/settings.xml"))

val commonScopes = listOf("compile", "provided", "runtime", "test", "system", "import")

val Project.mavenImportFlow
    get() = messageBus.flow(MavenImportListener.TOPIC) {
        object : MavenImportListener {
            override fun importFinished(
                importedProjects: MutableCollection<MavenProject>,
                newModules: MutableList<Module>,
            ) {
                trySend(Unit)
            }
        }
    }

fun getModuleChangesFlow(context: ProjectContext, pomPath: Path): Flow<Unit> = merge(
    watchExternalFileChanges(mavenSettingsFilePath),
    context.project.mavenImportFlow,
    context.project.smartModeFlow.mapUnit(),
    filesChangedEventFlow
        .map { it.mapNotNull { it.file?.toNioPathOrNull() } }
        .filter { it.any { it.isSameFileAsSafe(pomPath) } }
        .mapUnit(),
)

val xml = XML {
    defaultPolicy {
        ignoreUnknownChildren()
    }
}

private fun buildMavenParentHierarchy(pomFile: File): String {
    val pom = xml.decodeFromString<ProjectObjectModel>(POM_XML_NAMESPACE, pomFile.readText())
    val parentFile = pom.parent?.relativePath
        ?.let { pomFile.parentFile.resolve(it) }
        ?: return ":"
    val projectName = pom.artifactId ?: pomFile.parentFile.name
    val parentHierarchy = buildMavenParentHierarchy(parentFile)
    return parentHierarchy.suffixIfNot(":") + projectName
}

suspend fun Module.toPackageSearch(
    context: PackageSearchModuleBuilderContext,
    mavenProject: MavenProject,
): PackageSearchMavenModule {
    val declaredDependencies = getDeclaredDependencies(context)
    val pomPath = Path(mavenProject.file.path)
    return PackageSearchMavenModule(
        name = mavenProject.mavenId.artifactId ?: mavenProject.name ?: pomPath.parent.name,
        identity = PackageSearchModule.Identity(
            group = "maven",
            path = ":",
            projectDir = pomPath.parent,
        ),
        buildFilePath = pomPath,
        declaredRepositories = getDeclaredRepositories(context),
        declaredDependencies = declaredDependencies,
        availableScopes = commonScopes.plus(declaredDependencies.mapNotNull { it.declaredScope }).distinct(),
        compatiblePackageTypes = buildPackageTypes {
            jvmMavenPackages()
        },
        nativeModule = this
    )
}

private suspend fun Module.getDeclaredRepositories(context: PackageSearchModuleBuilderContext) =
    readAction { DependencyModifierService.getInstance(project).declaredRepositories(this) }
        .mapNotNull { unifiedRepository ->
            PackageSearchDeclaredMavenRepository(
                url = unifiedRepository.url ?: return@mapNotNull null,
                remoteInfo = context.knownRepositories.values.firstOrNull { it.url == unifiedRepository.url } as? ApiMavenRepository,
                name = unifiedRepository.name,
                id = unifiedRepository.id ?: return@mapNotNull null,
            )
        }

suspend fun Module.getDeclaredDependencies(context: PackageSearchModuleBuilderContext): List<PackageSearchDeclaredMavenPackage> {
    val declaredDependencies = readAction {
        MavenProjectsManager.getInstance(project)
            .findProject(this@getDeclaredDependencies)
            ?.file
            ?.let { MavenDomUtil.getMavenDomProjectModel(project, it) }
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

    val remoteInfo = context.getPackageInfoByIdHashes(distinctIds.map { ApiPackage.hashPackageId(it) }.toSet())

    return declaredDependencies
        .associateBy { it.packageId }
        .mapNotNull { (packageId, declaredDependency) ->
            PackageSearchDeclaredMavenPackage(
                id = packageId,
                declaredVersion = declaredDependency.version?.let { NormalizedVersion.from(it) },
                remoteInfo = remoteInfo[packageId]?.asMavenApiPackage(),
                groupId = declaredDependency.groupId,
                artifactId = declaredDependency.artifactId,
                declaredScope = declaredDependency.scope,
                declarationIndexes = declaredDependency.indexes,
                icon = remoteInfo[packageId]?.icon ?: IconProvider.Icons.MAVEN
            )
        }
}


fun EditModuleContext.validate(): MavenDependencyModificator {
    require(data is MavenDependencyModificator) {
        "Context must be EditMavenModuleContext"
    }
    return data as MavenDependencyModificator
}

val EditModuleContext.modificator
    get() = validate()

fun validateRepositoryType(repository: PackageSearchDeclaredRepository) {
    contract {
        returns() implies (repository is PackageSearchDeclaredMavenRepository)
    }
    require(repository is PackageSearchDeclaredMavenRepository) {
        "repository must be ApiMavenRepository instead of ${repository::class.qualifiedName}"
    }
}

fun PackageSearchDeclaredMavenRepository.toUnifiedRepository() =
    UnifiedDependencyRepository(id, name, url)