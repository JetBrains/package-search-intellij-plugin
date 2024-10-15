@file:Suppress("UnstableApiUsage")
@file:OptIn(ExperimentalContracts::class)

package com.jetbrains.packagesearch.plugin.gradle.utils

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.toNioPathOrNull
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredRepository
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.utils.filesChangedEventFlow
import com.jetbrains.packagesearch.plugin.core.utils.icon
import com.jetbrains.packagesearch.plugin.core.utils.isSameFileAsSafe
import com.jetbrains.packagesearch.plugin.core.utils.mapUnit
import com.jetbrains.packagesearch.plugin.core.utils.watchExternalFileChanges
import com.jetbrains.packagesearch.plugin.gradle.GradleDependencyModel
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleDeclaredPackage
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleDeclaredRepository
import com.jetbrains.packagesearch.plugin.gradle.packageId
import com.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleJavaModel
import java.nio.file.Paths
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.io.path.Path
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

fun getModuleChangesFlow(model: PackageSearchGradleJavaModel): Flow<Unit> {
    val knownFiles = buildSet {
        if (model.buildFilePath != null) {
            add(Path(model.buildFilePath))
        }
        addAll(
            knownGradleAncillaryFilesFiles.flatMap {
                listOf(
                    Path(model.rootProjectPath).resolve(it),
                    Path(model.projectDir).resolve(it),
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

suspend fun Module.retrieveGradleDependencyModel(): List<GradleDependencyModel> = readAction {
    ProjectBuildModel.get(this.project).getModuleBuildModel(this)
        ?.dependencies()
        ?.artifacts()
        ?.map { it.toGradleDependencyModel() }
        ?: emptyList()
}

suspend fun Module.getDeclaredDependencies(
    context: PackageSearchModuleBuilderContext,
): List<PackageSearchGradleDeclaredPackage> {
    val declaredDependencies = retrieveGradleDependencyModel()

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

fun List<PackageSearchGradleJavaModel.DeclaredRepository>.toGradle(context: PackageSearchModuleBuilderContext) =
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