@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.maven

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlText
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import com.jetbrains.packagesearch.plugin.core.utils.asMavenApiPackage
import com.jetbrains.packagesearch.plugin.core.utils.flow
import com.jetbrains.packagesearch.plugin.core.utils.icon
import java.io.File
import java.nio.file.Paths
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.jvmMavenPackages
import org.jetbrains.packagesearch.maven.POM_XML_NAMESPACE
import org.jetbrains.packagesearch.maven.ProjectObjectModel
import org.jetbrains.packagesearch.maven.decodeFromString
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

suspend fun Project.hasMaven(module: Module) =
    MavenProjectsManager.getInstance(this).let { readAction { it.findProject(module) } } != null

fun Module.hasExternalSystem() =
    ExternalSystemModulePropertyManager.getInstance(this).getExternalSystemId() != null

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

context(ProjectContext)
fun getModuleChangesFlow() = project.messageBus.flow(ModuleRootListener.TOPIC) {
    object : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
            if (event.project == project) {
                trySend(Unit)
            }
        }
    }
}

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

context(PackageSearchModuleBuilderContext)
suspend fun Module.toPackageSearch(): PackageSearchJpsModule {
    val declaredDependencies = getDeclaredDependencies()
    return PackageSearchJpsModule(
        name = mavenProject.name ?: name,
        identity = PackageSearchModule.Identity(
            group = "maven",
            path = buildMavenParentHierarchy(mavenProject.file.asRegularFile())
        ),
        buildFilePath = Paths.get(mavenProject.file.path),
        declaredKnownRepositories = getDeclaredKnownRepositories(),
        declaredDependencies = declaredDependencies,
        availableScopes = commonScopes.plus(declaredDependencies.mapNotNull { it.declaredScope }).distinct(),
        compatiblePackageTypes = buildPackageTypes {
            jvmMavenPackages()
        },
        nativeModule = this
    )
}

context(PackageSearchModuleBuilderContext)
suspend fun Module.getDeclaredDependencies(): List<PackageSearchDeclaredJpsPackage> {

    ModuleRootModificationUtil.updateModel(this) { model ->
        val libraries = model.moduleLibraryTable.libraries
        for (library in libraries) {
            val ex = library as? LibraryEx ?: continue
            val mavenCoordinates = (ex.properties as? RepositoryLibraryProperties)
                ?.mavenCoordinates
                ?: continue
            val id = "${mavenCoordinates.groupId}:${mavenCoordinates.artifactId}:${mavenCoordinates.version}"
            val declaredDependency = declaredDependencies.find { it.packageId == id } ?: continue
            val library = model.findLibraryOrderEntry(ex) ?: continue
            library.scope = declaredDependency.declaredScope
        }
    }

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

    val remoteInfo = getPackageInfoByIdHashes(distinctIds.map { ApiPackage.hashPackageId(it) }.toSet())

    return declaredDependencies
        .associateBy { it.packageId }
        .mapNotNull { (packageId, declaredDependency) ->
            PackageSearchDeclaredJpsPackage(
                id = packageId,
                declaredVersion = declaredDependency.version?.let { NormalizedVersion.fromStringOrNull(it) },
                remoteInfo = remoteInfo[packageId]?.asMavenApiPackage(),
                groupId = declaredDependency.groupId,
                artifactId = declaredDependency.artifactId,
                declaredScope = declaredDependency.scope,
                declarationIndexes = declaredDependency.indexes,
                icon = remoteInfo[packageId]?.icon ?: IconProvider.Icons.MAVEN
            )
        }
}

context(PackageSearchModuleBuilderContext)
suspend fun Module.getDeclaredKnownRepositories(): Map<String, ApiRepository> {
    val declaredDependencies = readAction {
        DependencyModifierService.getInstance(project)
            .declaredRepositories(this)
    }
        .mapNotNull { it.id }
    return knownRepositories.filterKeys { it in declaredDependencies }
}

context(EditModuleContext)
internal fun validateContextType(): JpsActions {
    require(data is JpsActions) {
        "Context must be JpsDownloader"
    }
    return data as JpsActions
}

context(EditModuleContext)
internal val actions
    get() = validateContextType()