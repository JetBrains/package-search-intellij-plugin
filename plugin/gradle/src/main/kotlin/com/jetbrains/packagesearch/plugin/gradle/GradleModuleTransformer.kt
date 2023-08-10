@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.packagesearch.plugin.core.data.WithIcon.Icons
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleProvider
import com.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import com.jetbrains.packagesearch.plugin.core.nitrite.NitriteFilters
import com.jetbrains.packagesearch.plugin.core.nitrite.div
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.asMavenApiPackage
import com.jetbrains.packagesearch.plugin.core.utils.collectIn
import com.jetbrains.packagesearch.plugin.core.utils.filesChangedEventFlow
import com.jetbrains.packagesearch.plugin.core.utils.mapUnit
import com.jetbrains.packagesearch.plugin.core.utils.packageId
import com.jetbrains.packagesearch.plugin.core.utils.registryStateFlow
import com.jetbrains.packagesearch.plugin.core.utils.watchExternalFileChanges
import com.jetbrains.packagesearch.plugin.gradle.utils.dumbModeStateFlow
import com.jetbrains.packagesearch.plugin.gradle.utils.evaluateDeclaredIndexes
import com.jetbrains.packagesearch.plugin.gradle.utils.getGradleModelRepository
import com.jetbrains.packagesearch.plugin.gradle.utils.gradleIdentityPathOrNull
import com.jetbrains.packagesearch.plugin.gradle.utils.gradleSyncNotifierFlow
import com.jetbrains.packagesearch.plugin.gradle.utils.isGradleSourceSet
import java.nio.file.Path
import kotlin.io.path.exists
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import com.intellij.openapi.module.Module as NativeModule

abstract class BaseGradleModuleProvider : PackageSearchModuleProvider {

    companion object {

        val gradleHomePathString: String
            get() = System.getenv("GRADLE_HOME") ?: System.getProperty("user.home")

        val gradleHome
            get() = gradleHomePathString.toNioPath()

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
            "androidTestRuntimeOnly"
        )

        fun getModuleChangesFlow(
            context: ProjectContext,
            buildFile: String?,
        ): Flow<Unit> {
            val allFiles = if (buildFile != null) {
                knownGradleAncillaryFilesFiles + buildFile
            } else {
                knownGradleAncillaryFilesFiles
            }
            val buildFileChanges = context
                .project
                .filesChangedEventFlow
                .flatMapLatest { it.map { it.path }.asFlow() }
                .filter { filePath -> allFiles.any { filePath.endsWith(it) } }
                .mapUnit()
            return merge(
                watchExternalFileChanges(globalGradlePropertiesPath),
                buildFileChanges,
                IntelliJApplication.registryStateFlow(
                    context.coroutineScope,
                    "org.jetbrains.packagesearch.localhost",
                    false
                )
                    .mapUnit()
            )
        }

        suspend fun NativeModule.getDeclaredKnownRepositories(
            context: PackageSearchModuleBuilderContext,
        ): Map<String, ApiRepository> {
            val declaredDependencies = readAction {
                DependencyModifierService.getInstance(project).declaredRepositories(this)
            }.mapNotNull { it.id }
            return context.knownRepositories.filterKeys { it in declaredDependencies }
        }

        suspend fun Module.getDeclaredDependencies(
            context: PackageSearchModuleBuilderContext,
            isKts: Boolean,
        ): List<PackageSearchGradleDeclaredPackage> {
            val declaredDependencies = readAction {
                DependencyModifierService.getInstance(context.project)
                    .declaredDependencies(this)
            }
                .distinctBy { it.unifiedDependency }

            val distinctIds = declaredDependencies
                .asSequence()
                .filter { it.coordinates.artifactId != null && it.coordinates.groupId != null }
                .map { it.packageId }
                .distinct()
            val isLocalhost = Registry.`is`("org.jetbrains.packagesearch.localhost", false)
            val remoteInfo =
                if (!isLocalhost) {
                    context.getPackageInfoByIdHashes(distinctIds.map { ApiPackage.hashPackageId(it) }.toSet())
                } else {
                    context.getPackageInfoByIds(distinctIds.toSet())
                }

            return declaredDependencies
                .associateBy { it.packageId }
                .mapNotNull { (packageId, declaredDependency) ->
                    PackageSearchGradleDeclaredPackage(
                        id = packageId,
                        declaredVersion = NormalizedVersion.from(declaredDependency.coordinates.version),
                        latestStableVersion = remoteInfo[packageId]?.versions?.latestStable?.normalized
                            ?: NormalizedVersion.Missing,
                        latestVersion = remoteInfo[packageId]?.versions?.latest?.normalized
                            ?: NormalizedVersion.Missing,
                        remoteInfo = remoteInfo[packageId]?.asMavenApiPackage(),
                        icon = Icons.GRADLE,
                        module = declaredDependency.coordinates.groupId ?: return@mapNotNull null,
                        name = declaredDependency.coordinates.artifactId ?: return@mapNotNull null,
                        configuration = declaredDependency.unifiedDependency.scope ?: error(
                            "No scope available for ${declaredDependency.unifiedDependency}" + " in module $name"
                        ),
                        declarationIndexes = declaredDependency.evaluateDeclaredIndexes(isKts),
                    )
                }
        }

    }

    override fun provideModule(
        context: PackageSearchModuleBuilderContext,
        nativeModule: NativeModule,
    ): Flow<PackageSearchModuleData?> = when {
        nativeModule.isGradleSourceSet -> flowOf(null)
        else -> merge(
            flowOf(Unit),
            context.project.dumbModeStateFlow
                .filterNot { it }
                .mapUnit(),
            context.project.gradleSyncNotifierFlow
        )
            .mapNotNull { nativeModule.gradleIdentityPathOrNull }
            .flatMapLatest {
                flow {
                    context.getGradleModelRepository()
                        .find(
                            NitriteFilters.Object.eq(
                                path = GradleModelCacheEntry::data / PackageSearchGradleModel::projectIdentityPath,
                                value = it
                            )
                        )
                        .singleOrNull()
                        ?.data
                        ?.let { emit(it) }
                    context.getGradleModelRepository()
                        .changes()
                        .flatMapLatest { it.changedItems.asFlow().map { it.item.data } }
                        .collectIn(this)
                }
            }
            .filter { it.projectIdentityPath == nativeModule.gradleIdentityPathOrNull }
            .map { model ->
                val basePath = model.projectDir.toNioPath()
                val buildFile = basePath.resolve("build.gradle")
                    .takeIf { it.exists() }
                    ?: basePath.resolve("build.gradle.kts")
                        .takeIf { it.exists() }
                model to buildFile
            }
            .flatMapLatest { data ->
                flow {
                    emit(data)
                    getModuleChangesFlow(context, data.second?.toAbsolutePath()?.toString())
                        .map { data }
                        .collectIn(this)
                }
            }
            .map { (model, buildFile) ->
                nativeModule.transform(context, model, buildFile)
            }
    }

    abstract suspend fun Module.transform(
        context: PackageSearchModuleBuilderContext,
        model: PackageSearchGradleModel,
        buildFile: Path?
    ): PackageSearchModuleData?

}
