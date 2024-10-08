@file:Suppress("UnstableApiUsage")
@file:OptIn(ExperimentalContracts::class)

package com.jetbrains.packagesearch.plugin.gradle.utils

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredRepository
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchProjectCachesService
import com.jetbrains.packagesearch.plugin.core.utils.filesChangedEventFlow
import com.jetbrains.packagesearch.plugin.core.utils.icon
import com.jetbrains.packagesearch.plugin.core.utils.isSameFileAsSafe
import com.jetbrains.packagesearch.plugin.core.utils.mapUnit
import com.jetbrains.packagesearch.plugin.core.utils.watchExternalFileChanges
import com.jetbrains.packagesearch.plugin.gradle.GradleDependencyModel
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleDeclaredPackage
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleDeclaredRepository
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModel
import com.jetbrains.packagesearch.plugin.gradle.packageId
import java.nio.file.Path
import java.nio.file.Paths
import korlibs.crypto.sha512
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.dizitart.kno2.filters.eq
import org.dizitart.no2.collection.UpdateOptions
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

val gradleHomePathString: String
    get() = System.getenv("GRADLE_HOME") ?: System.getProperty("user.home")

val gradleHome
    get() = Paths.get(gradleHomePathString)

val globalGradlePropertiesPath
    get() = gradleHome.resolve("gradle.properties")

val knownGradleAncillaryFilesFiles
    get() = listOf("gradle.properties", "local.properties", "gradle/libs.versions.toml")

fun getModuleChangesFlow(model: PackageSearchGradleModel): Flow<Unit> {
    val knownFiles = buildSet {
        if (model.buildFilePath != null) {
            add(model.buildFilePath)
        }
        addAll(
            knownGradleAncillaryFilesFiles.flatMap {
                listOf(
                    model.rootProjectPath.resolve(it),
                    model.projectDir.resolve(it),
                )
            }
        )
    }

    val buildFileChanges = filesChangedEventFlow
        .map { it.mapNotNull { it.file?.toNioPathOrNull() } }
        .filter { changes -> changes.any { change -> knownFiles.any { it.isSameFileAsSafe(change) } } }
        .mapUnit()

    return merge(
        watchExternalFileChanges(globalGradlePropertiesPath),
        buildFileChanges,
    )
}

@Serializable
data class GradleDependencyModelCacheEntry(
    @SerialName("_id") val id: String? = null,
    val buildFile: String,
    val buildFileSha: String,
    val dependencies: List<GradleDependencyModel>,
)

suspend fun PackageSearchModuleBuilderContext.retrieveGradleDependencyModel(nativeModule: Module, buildFile: Path): List<GradleDependencyModel> {
    val vf = buildFile.refreshAndFindVirtualFile() ?: return emptyList()

    val buildFileSha = vf.contentsToByteArray().sha512().hex

    val cache = withContext(Dispatchers.IO) {
        project.service<GradleCacheService>()
            .dependencyRepository
            .find(GradleDependencyModelCacheEntry::buildFile eq buildFile.absolutePathString())
            .singleOrNull()
    }

    if (cache?.buildFileSha == buildFileSha) return cache.dependencies

    val dependencies = readAction {
        ProjectBuildModel.get(nativeModule.project).getModuleBuildModel(nativeModule)
            ?.dependencies()
            ?.artifacts()
            ?.map { it.toGradleDependencyModel() }
            ?: emptyList()
    }

    project.service<GradleCacheService>()
        .dependencyRepository
        .update(
            /* filter = */ GradleDependencyModelCacheEntry::buildFile eq buildFile.absolutePathString(),
            /* update = */ GradleDependencyModelCacheEntry(
                buildFile = buildFile.absolutePathString(),
                buildFileSha = buildFileSha,
                dependencies = dependencies
            ),
            /* updateOptions = */ UpdateOptions.updateOptions(true)
        )

    return dependencies
}

@Service(Service.Level.PROJECT)
class GradleCacheService(project: Project) : Disposable {
    val dependencyRepository =
        project.PackageSearchProjectCachesService.getRepository<GradleDependencyModelCacheEntry>("gradle-dependencies")

    override fun dispose() = dependencyRepository.close()
}

suspend fun Module.getDeclaredDependencies(
    context: PackageSearchModuleBuilderContext,
    buildFile: Path
): List<PackageSearchGradleDeclaredPackage> {
    val declaredDependencies = context.retrieveGradleDependencyModel(this, buildFile)

    val distinctIds = declaredDependencies
        .asSequence()
        .map { it.packageId }
        .distinct()

    val remoteInfo = context.getPackageInfoByIdHashes(distinctIds.map { ApiPackage.hashPackageId(it) }.toSet())

    return declaredDependencies
        .map { declaredDependency ->
            PackageSearchGradleDeclaredPackage(
                id = declaredDependency.packageId,
                declaredVersion = declaredDependency.version?.let { NormalizedVersion.from(it) },
                remoteInfo = remoteInfo[declaredDependency.packageId] as? ApiMavenPackage,
                icon = remoteInfo[declaredDependency.packageId]?.icon
                    ?: IconProvider.Icons.MAVEN,
                module = declaredDependency.groupId,
                name = declaredDependency.artifactId,
                configuration = declaredDependency.configuration,
                declarationIndexes = declaredDependency.indexes,
            )
        }
}

internal val Project.initializeProjectFlow
    get() = flow {
        awaitExternalSystemInitialization()
        emit(Unit)
    }

fun List<PackageSearchGradleModel.DeclaredRepository>.toGradle(context: PackageSearchModuleBuilderContext) =
    map {
        PackageSearchGradleDeclaredRepository(
            url = it.url,
            remoteInfo = context.knownRepositories.values
                .firstOrNull { remote -> remote.url == it.url } as? ApiMavenRepository,
            name = it.name,
        )
    }

fun validateRepositoryType(repository: PackageSearchDeclaredRepository) {
    contract {
        returns() implies (repository is PackageSearchGradleDeclaredRepository)
    }
    require(repository is PackageSearchGradleDeclaredRepository) {
        "Repository ${repository.url} must be PackageSearchGradleDeclaredRepository"
    }
}

fun PackageSearchGradleDeclaredRepository.toUnifiedRepository() =
    UnifiedDependencyRepository(null, name, url)