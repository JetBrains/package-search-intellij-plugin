@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle.utils

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.asMavenApiPackage
import com.jetbrains.packagesearch.plugin.core.utils.filesChangedEventFlow
import com.jetbrains.packagesearch.plugin.core.utils.icon
import com.jetbrains.packagesearch.plugin.core.utils.mapUnit
import com.jetbrains.packagesearch.plugin.core.utils.registryFlow
import com.jetbrains.packagesearch.plugin.core.utils.watchExternalFileChanges
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleDeclaredPackage
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModel
import java.nio.file.Paths
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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

fun getModuleChangesFlow(
    model: PackageSearchGradleModel,
): Flow<Unit> {
    val allFiles = buildSet {
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
        .flatMapConcat { it.map { it.path }.asFlow() }
        .map { Paths.get(it) }
        .filter { filePath -> allFiles.any { filePath == it } }
        .mapUnit()

    return merge(
        watchExternalFileChanges(globalGradlePropertiesPath),
        buildFileChanges,
        IntelliJApplication.registryFlow("packagesearch.sonatype.api.client").mapUnit(),
    )
}

context(PackageSearchModuleBuilderContext)
suspend fun Module.getDeclaredKnownRepositories(): Map<String, ApiRepository> {
    val declaredDependencies = readAction {
        DependencyModifierService.getInstance(project).declaredRepositories(this)
    }.mapNotNull { it.id }
    return knownRepositories.filterKeys { it in declaredDependencies }
}

context(PackageSearchModuleBuilderContext)
suspend fun Module.getDeclaredDependencies(): List<PackageSearchGradleDeclaredPackage> {
    val declaredDependencies = readAction {
        ProjectBuildModel.get(project).getModuleBuildModel(this)
            ?.dependencies()
            ?.artifacts()
            ?.map { it.toGradleDependencyModel() }
            ?: emptyList()
    }.distinct()

    val distinctIds = declaredDependencies
        .asSequence()
        .map { it.packageId }
        .distinct()

    val remoteInfo = getPackageInfoByIdHashes(distinctIds.map { ApiPackage.hashPackageId(it) }.toSet())

    return declaredDependencies
        .mapNotNull { declaredDependency ->
            PackageSearchGradleDeclaredPackage(
                id = declaredDependency.packageId,
                declaredVersion = declaredDependency.version?.let { NormalizedVersion.fromStringOrNull(it) },
                remoteInfo = remoteInfo[declaredDependency.packageId] as? ApiMavenPackage
                    ?: return@mapNotNull null,
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