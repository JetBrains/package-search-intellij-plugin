@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.packagesearch.plugin.core.data.IconProvider.Icons
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
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
import com.jetbrains.packagesearch.plugin.core.utils.registryStateFlow
import com.jetbrains.packagesearch.plugin.core.utils.watchExternalFileChanges
import com.jetbrains.packagesearch.plugin.gradle.utils.dumbModeStateFlow
import com.jetbrains.packagesearch.plugin.gradle.utils.getGradleModelRepository
import com.jetbrains.packagesearch.plugin.gradle.utils.gradleIdentityPathOrNull
import com.jetbrains.packagesearch.plugin.gradle.utils.gradleSyncNotifierFlow
import com.jetbrains.packagesearch.plugin.gradle.utils.isGradleSourceSet
import com.jetbrains.packagesearch.plugin.gradle.utils.toGradleDependencyModel
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
import java.nio.file.Path
import kotlin.io.path.exists
import com.intellij.openapi.module.Module as NativeModule

class GradleDependencyModel(
    val groupId: String,
    val artifactId: String,
    val version: String?,
    val configuration: String,
    val indexes: DependencyDeclarationIndexes,
) {

    val packageId
        get() = "maven:$groupId:$artifactId"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GradleDependencyModel

        if (groupId != other.groupId) return false
        if (artifactId != other.artifactId) return false
        if (version != other.version) return false
        if (configuration != other.configuration) return false

        return true
    }

    override fun hashCode(): Int {
        var result = groupId.hashCode()
        result = 31 * result + artifactId.hashCode()
        result = 31 * result + (version?.hashCode() ?: 0)
        result = 31 * result + (configuration?.hashCode() ?: 0)
        return result
    }
}
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
            "androidTestRuntimeOnly",
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
                    false,
                )
                    .mapUnit(),
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

            val isLocalhost = Registry.`is`("org.jetbrains.packagesearch.localhost", false)

            val remoteInfo =
                if (!isLocalhost) {
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
                        lightIconPath = Icons.GRADLE_LIGHT,
                        module = declaredDependency.groupId,
                        name = declaredDependency.artifactId,
                        configuration = declaredDependency.configuration,
                        declarationIndexes = declaredDependency.indexes,
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
            context.project.gradleSyncNotifierFlow,
        )
            .mapNotNull { nativeModule.gradleIdentityPathOrNull }
            .flatMapLatest {
                flow {
                    context.getGradleModelRepository()
                        .find(
                            NitriteFilters.Object.eq(
                                path = GradleModelCacheEntry::data / PackageSearchGradleModel::projectIdentityPath,
                                value = it,
                            ),
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
        buildFile: Path?,
    ): PackageSearchModuleData?
}
