@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle.utils

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.nitrite.NitriteFilters
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchProjectCachesService
import com.jetbrains.packagesearch.plugin.core.utils.filesChangedEventFlow
import com.jetbrains.packagesearch.plugin.core.utils.icon
import com.jetbrains.packagesearch.plugin.core.utils.isSameFileAsSafe
import com.jetbrains.packagesearch.plugin.core.utils.mapUnit
import com.jetbrains.packagesearch.plugin.core.utils.watchExternalFileChanges
import com.jetbrains.packagesearch.plugin.gradle.GradleDependencyModel
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleDeclaredPackage
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModel
import com.jetbrains.packagesearch.plugin.gradle.packageId
import java.nio.file.Path
import java.nio.file.Paths
import korlibs.crypto.sha512
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
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

context(PackageSearchModuleBuilderContext)
suspend fun Module.getDeclaredKnownRepositories(repositories: List<String>): Map<String, ApiRepository> {
    val declaredDependencies = readAction {
        DependencyModifierService.getInstance(project).declaredRepositories(this)
    }.mapNotNull { it.id }
    return knownRepositories.filterKeys { it in declaredDependencies } + knownRepositories.filterValues { it.url in repositories }
}

@Serializable
data class GradleDependencyModelCacheEntry(
    @SerialName("_id") val id: Long? = null,
    val buildFile: String,
    val buildFileSha: String,
    val dependencies: List<GradleDependencyModel>,
)

context(PackageSearchModuleBuilderContext)
suspend fun retrieveGradleDependencyModel(nativeModule: Module, buildFile: Path): List<GradleDependencyModel> {
    val vf = buildFile.refreshAndFindVirtualFile() ?: return emptyList()

    val buildFileSha = vf.contentsToByteArray().sha512().hex

    val cache = project.service<GradleCacheService>()
        .dependencyRepository
        .find(
            filter = NitriteFilters.Object.eq(
                path = GradleDependencyModelCacheEntry::buildFile,
                value = buildFile.absolutePathString()
            )
        ).singleOrNull()

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
            filter = NitriteFilters.Object.eq(
                path = GradleDependencyModelCacheEntry::buildFile,
                value = buildFile.absolutePathString()
            ),
            update = GradleDependencyModelCacheEntry(
                buildFile = buildFile.absolutePathString(),
                buildFileSha = buildFileSha,
                dependencies = dependencies
            ),
            upsert = true
        )

    return dependencies
}

@Service(Service.Level.PROJECT)
class GradleCacheService(project: Project) : Disposable {
    val dependencyRepository =
        project.PackageSearchProjectCachesService.getRepository<GradleDependencyModelCacheEntry>("gradle-dependencies")

    override fun dispose() = dependencyRepository.close()
}

context(PackageSearchModuleBuilderContext)
suspend fun Module.getDeclaredDependencies(buildFile: Path): List<PackageSearchGradleDeclaredPackage> {
    val declaredDependencies = retrieveGradleDependencyModel(this, buildFile)

    val distinctIds = declaredDependencies
        .asSequence()
        .map { it.packageId }
        .distinct()

    val remoteInfo = getPackageInfoByIdHashes(distinctIds.map { ApiPackage.hashPackageId(it) }.toSet())

    return declaredDependencies
        .map { declaredDependency ->
            PackageSearchGradleDeclaredPackage(
                id = declaredDependency.packageId,
                declaredVersion = declaredDependency.version?.let { NormalizedVersion.fromStringOrNull(it) },
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

internal fun isResolveTask(id: ExternalSystemTaskId): Boolean {
    if (id.type == ExternalSystemTaskType.RESOLVE_PROJECT) {
        val task = IntelliJApplication.service<ExternalSystemProcessingManager>()
            .findTask(id)
        if (task is ExternalSystemResolveProjectTask) {
            return !task.isPreviewMode
        }
    }
    return false
}