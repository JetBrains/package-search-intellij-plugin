@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.maven

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.asMavenApiPackage
import com.jetbrains.packagesearch.plugin.core.utils.filesChangedEventFlow
import com.jetbrains.packagesearch.plugin.core.utils.flow
import com.jetbrains.packagesearch.plugin.core.utils.getIcon
import com.jetbrains.packagesearch.plugin.core.utils.mapUnit
import com.jetbrains.packagesearch.plugin.core.utils.registryFlow
import com.jetbrains.packagesearch.plugin.core.utils.watchExternalFileChanges
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.javaApi
import org.jetbrains.packagesearch.api.v3.search.javaRuntime
import org.jetbrains.packagesearch.maven.POM_XML_NAMESPACE
import org.jetbrains.packagesearch.maven.ProjectObjectModel
import org.jetbrains.packagesearch.maven.decodeFromString
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

suspend fun Project.findMavenProjectFor(module: Module): MavenProject? =
    MavenProjectsManager.getInstance(this).let { readAction { it.findProject(module) } }

suspend fun DeclaredDependency.evaluateDeclaredIndexes() = readAction {
    val children: Array<PsiElement> = (psiElement as? XmlTag)
        ?.children
        ?: return@readAction null
    val xmlTag = children.filterIsInstance<XmlText>()
        .find { it is Navigatable && it.canNavigate() }
        ?: return@readAction null
    DependencyDeclarationIndexes(
        declarationStartIndex = xmlTag.textOffset,
        versionStartIndex = children.filterIsInstance<XmlTag>()
            .find { it.name == "version" }
            ?.children
            ?.filterIsInstance<XmlText>()
            ?.firstOrNull()
            ?.textOffset
    )
}

fun VirtualFile.asRegularFile() = File(path)
fun String.suffixIfNot(s: String) = if (endsWith(s)) this else this + s

val mavenSettingsFilePath
    get() = System.getenv("M2_HOME")
        ?.let { Paths.get("$it/conf/settings.xml") }
        ?: Paths.get(System.getProperty("user.home").plus("/.m2/settings.xml"))

val commonScopes = listOf("compile", "provided", "runtime", "test", "system", "import")

val Project.mavenImportFlow
    get() = messageBus.flow(MavenImportListener.TOPIC) {
        MavenImportListener { _, _ ->
            trySend(Unit)
        }
    }

fun getModuleChangesFlow(context: ProjectContext, pomPath: Path): Flow<Unit> = merge(
    watchExternalFileChanges(mavenSettingsFilePath),
    context.project.mavenImportFlow,
    context.project.filesChangedEventFlow
        .flatMapLatest { it.map { it.path }.asFlow() }
        .map { Paths.get(it) }
        .filter { it == pomPath }
        .mapUnit(),
    IntelliJApplication.registryFlow("packagesearch.sonatype.api.client").mapUnit()
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
        buildFilePath = Paths.get(mavenProject.file.path),
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

    val isSonatype = Registry.`is`("packagesearch.sonatype.api.client")

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
                icon = remoteInfo[packageId]?.getIcon(declaredDependency.version) ?: IconProvider.Icons.MAVEN
            )
        }
}

suspend fun Module.getDeclaredKnownRepositories(context: PackageSearchModuleBuilderContext): Map<String, ApiRepository> {
    val declaredDependencies = readAction {
        DependencyModifierService.getInstance(project)
            .declaredRepositories(this)
    }
        .mapNotNull { it.id }
    return context.knownRepositories.filterKeys { it in declaredDependencies }
}

