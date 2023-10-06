@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle.utils

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.asMavenApiPackage
import com.jetbrains.packagesearch.plugin.core.utils.filesChangedEventFlow
import com.jetbrains.packagesearch.plugin.core.utils.getIcon
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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

val commonConfigurations = setOf(
    "implementation",
    "api",
    "compileOnly",
    "runtimeOnly",
    "testImplementation",
    "testCompileOnly",
    "testRuntimeOnly",
    "annotationProcessor",
    "detektPlugins",
    "kapt",
    "ksp",
    "androidTestImplementation",
    "androidTestCompileOnly",
    "androidTestRuntimeOnly",
)

fun getModuleChangesFlow(
    context: ProjectContext,
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

    val buildFileChanges = context
        .project
        .filesChangedEventFlow
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

suspend fun Module.getDeclaredKnownRepositories(
    context: PackageSearchModuleBuilderContext,
): Map<String, ApiRepository> {
    val declaredDependencies = readAction {
        DependencyModifierService.getInstance(project).declaredRepositories(this)
    }.mapNotNull { it.id }
    return context.knownRepositories.filterKeys { it in declaredDependencies }
}

suspend fun Module.getDeclaredDependencies(
    context: PackageSearchModuleBuilderContext,
): List<PackageSearchGradleDeclaredPackage> {
    val declaredDependencies = readAction {
        ProjectBuildModel.get(context.project).getModuleBuildModel(this)
            ?.dependencies()
            ?.artifacts()
            ?.map { it.toGradleDependencyModel() }
            ?: emptyList()
    }.distinct()

    val distinctIds = declaredDependencies
        .asSequence()
        .map { it.packageId }
        .distinct()

    val isSonatype = Registry.`is`("packagesearch.sonatype.api.client")

    val remoteInfo =
        if (!isSonatype) {
            context.getPackageInfoByIdHashes(distinctIds.map { ApiPackage.hashPackageId(it) }.toSet())
        } else {
            context.getPackageInfoByIds(distinctIds.toSet())
        }

    return declaredDependencies
        .map { declaredDependency ->
            PackageSearchGradleDeclaredPackage(
                id = declaredDependency.packageId,
                declaredVersion = NormalizedVersion.from(declaredDependency.version),
                latestStableVersion = remoteInfo[declaredDependency.packageId]?.versions?.latestStable?.normalized
                    ?: NormalizedVersion.Missing,
                latestVersion = remoteInfo[declaredDependency.packageId]?.versions?.latest?.normalized
                    ?: NormalizedVersion.Missing,
                remoteInfo = remoteInfo[declaredDependency.packageId]?.asMavenApiPackage(),
                icon = remoteInfo[declaredDependency.packageId]?.getIcon(declaredDependency.version)
                    ?: IconProvider.Icons.MAVEN,
                module = declaredDependency.groupId,
                name = declaredDependency.artifactId,
                configuration = declaredDependency.configuration,
                declarationIndexes = declaredDependency.indexes,
            )
        }
}
