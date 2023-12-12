@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.maven

import com.intellij.jarRepository.RepositoryLibraryType
import com.intellij.java.library.LibraryWithMavenCoordinatesProperties
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.IconProvider.Icons
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.utils.validateMavenDeclaredPackageType
import com.jetbrains.packagesearch.plugin.core.utils.validateMavenPackageType
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType

@Serializable
@SerialName("jps")
data class PackageSearchJpsModule(
    override val name: String,
    override val identity: PackageSearchModule.Identity,
    override val buildFilePath: Path?,
    override val declaredKnownRepositories: Map<String, ApiMavenRepository>,
    override val declaredDependencies: List<PackageSearchDeclaredJpsPackage>,
    override val defaultScope: String? = null,
    override val availableScopes: List<String>,
    override val compatiblePackageTypes: List<PackagesType>,
    private val nativeModule: Module,
) : PackageSearchModule.Base {

    override val dependencyMustHaveAScope: Boolean
        get() = true

    override val icon
        get() = Icons.IDEA

    override suspend fun editModule(action: context(EditModuleContext) () -> Unit) {
        val libsDir = ModuleRootManager.getInstance(nativeModule)
            .contentRoots
            .firstOrNull()
            ?.runCatching { toNioPath() }
            ?.getOrNull()
            ?.resolve("libs")
            ?.createDirectories()
            ?: return
        val editContext = EditJpsModuleContext()
        action(editContext)
        val toModify = editContext.data.getToModify()
        val toRemove = editContext.data.getToRemove()
        if (toModify.isNotEmpty() || toRemove.isNotEmpty()) {
            ModuleRootModificationUtil.updateModel(nativeModule) { model ->
                val mavenLibraries = buildMap {
                    for (lib in model.moduleLibraryTable.libraries) {
                        val ex = lib as? LibraryEx ?: continue
                        val mavenCoordinates = (ex.properties as? LibraryWithMavenCoordinatesProperties)
                            ?.mavenCoordinates
                            ?: continue
                        val id = "${mavenCoordinates.groupId}:${mavenCoordinates.artifactId}" +
                                ":${mavenCoordinates.version}"
                        put(id, ex)
                    }
                }
                for (modification in toModify) {
                    val libraryEx = mavenLibraries[modification.id] ?: continue
                    val library = model.findLibraryOrderEntry(libraryEx) ?: continue
                    library.scope = modification.newScope
                }
                for (removal in toRemove) {
                    val libraryEx = mavenLibraries[removal] ?: continue
                    model.moduleLibraryTable.modifiableModel.removeLibrary(libraryEx)
                }
                model.commit()
            }
        }
        val downloader = JpsDownloader(libsDir)
        val downloaded = editContext.data.getToAdd()
            .associateWith { downloader.downloadLibrary(it.groupId, it.artifactId, it.version, it.repositories) }
        val virtualFileManager = VirtualFileManager.getInstance()
        ModuleRootModificationUtil.updateModel(nativeModule) { model ->
            for ((add, libraries) in downloaded) {
                for (library in libraries) {
                    val libraryEx =
                        (model.moduleLibraryTable.modifiableModel
                            .createLibrary(add.id, RepositoryLibraryType.REPOSITORY_LIBRARY_KIND) as? LibraryEx)
                            ?.modifiableModel
                            ?: continue
                    val properties = RepositoryLibraryProperties(
                        /* groupId = */ add.groupId,
                        /* artifactId = */ add.artifactId,
                        /* version = */ add.version,
                        /* includeTransitiveDependencies = */ true,
                        /* excludedDependencies = */ emptyList()
                    )
                    libraryEx.properties = properties
                    virtualFileManager.findFileByNioPath(library.classesPath)
                        ?.let { libraryEx.addRoot(it, OrderRootType.CLASSES) }
                    virtualFileManager.findFileByNioPath(library.sourcesPath)
                        ?.let { libraryEx.addRoot(it, OrderRootType.SOURCES) }
                    libraryEx.commit()
                }

            }
            model.commit()
        }

    }

    context(EditModuleContext)
    override fun updateDependency(
        declaredPackage: PackageSearchDeclaredPackage,
        newVersion: String?,
        newScope: String?,
    ) {
        validateMavenDeclaredPackageType(declaredPackage)
        actions.modify(
            scope = DependencyScope.entries.firstOrNull { it.name.equals(newScope, true) }
                ?: DependencyScope.COMPILE,
            declaredPackage = declaredPackage,
            newVersion = newVersion ?: declaredPackage.declaredVersion?.toString() ?: return,
            knownRepositories = declaredKnownRepositories
        )
    }

    context(EditModuleContext)
    override fun addDependency(
        apiPackage: ApiPackage,
        selectedVersion: String,
        selectedScope: String?,
    ) {
        validateMavenPackageType(apiPackage)
        val repositories = apiPackage.versions.all
            .firstOrNull { it.normalizedVersion.versionName == selectedVersion }
            ?.repositoryIds
            ?.mapNotNull { declaredKnownRepositories[it] }
            ?.takeIf { it.isNotEmpty() }
            ?: return

        actions.add(
            groupId = apiPackage.groupId,
            artifactId = apiPackage.artifactId,
            version = selectedVersion,
            scope = DependencyScope.entries.firstOrNull { it.name.equals(selectedScope, true) }
                ?: DependencyScope.COMPILE,
            repositories = repositories
        )
    }

    context(EditModuleContext)
    override fun removeDependency(declaredPackage: PackageSearchDeclaredPackage) {
        validateMavenDeclaredPackageType(declaredPackage)
        actions.remove(declaredPackage)
    }

    context(EditModuleContext)
    override fun addRepository(repository: ApiRepository) {

    }

    context(EditModuleContext)
    override fun removeRepository(repository: ApiRepository) {

    }
}
