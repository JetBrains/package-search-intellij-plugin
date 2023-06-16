@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.toNioPath
import kotlinx.coroutines.flow.*
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.buildPackagesType
import org.jetbrains.packagesearch.api.v3.search.javaApi
import org.jetbrains.packagesearch.api.v3.search.javaRuntime
import org.jetbrains.packagesearch.api.v3.search.libraryElements
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.data.WithIcon.Icons
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleTransformer
import org.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import org.jetbrains.packagesearch.plugin.core.nitrite.NitriteFilters
import org.jetbrains.packagesearch.plugin.core.nitrite.div
import org.jetbrains.packagesearch.plugin.core.utils.*
import org.jetbrains.packagesearch.plugin.gradle.utils.evaluateDeclaredIndexes
import org.jetbrains.packagesearch.plugin.gradle.utils.getGradleModelRepository
import org.jetbrains.packagesearch.plugin.gradle.utils.gradleIdentityPathOrNull
import org.jetbrains.packagesearch.plugin.gradle.utils.isGradleSourceSet
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.extension
import com.intellij.openapi.module.Module as NativeModule


class GradleModuleTransformer : PackageSearchModuleTransformer {

    companion object Utils {

        val gradleHomePathString: String
            get() = System.getenv("GRADLE_HOME") ?: System.getProperty("user.home")

        val gradleHome
            get() = gradleHomePathString.toNioPath()

        val globalGradlePropertiesPath
            get() = gradleHome.resolve("gradle.properties")

        val knownGradleAncillaryFilesFiles
            get() = listOf("gradle.properties", "local.properties", "gradle/libs.versions.toml")

    }

    override fun PolymorphicModuleBuilder<PackageSearchModule>.registerModuleSerializer() {
        subclass(PackageSearchGradleModule.serializer())
    }

    override fun PolymorphicModuleBuilder<PackageSearchDeclaredDependency>.registerVersionSerializer() {
        subclass(PackageSearchDeclaredGradleDependency.serializer())
    }

    private fun getModuleChangesFlow(
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
            buildFileChanges
        )
    }

    private suspend fun Module.toPackageSearch(
        context: PackageSearchModuleBuilderContext,
        model: PackageSearchGradleModel,
        buildFile: Path?,
    ): PackageSearchGradleModule {
        val availableKnownRepositories =
            model.repositories.toSet().let { availableGradleRepositories ->
                context.knownRepositories.filterValues {
                    it is ApiMavenRepository && it.alternateUrls.intersect(availableGradleRepositories).isNotEmpty()
                }
            }

        val isKts = buildFile?.extension == "kts"

        return PackageSearchGradleModule(
            name = model.projectName,
            projectDirPath = model.projectDir,
            buildFilePath = buildFile?.absolutePathString(),
            declaredKnownRepositories = getDeclaredKnownRepositories(context),
            declaredDependencies = getDeclaredDependencies(context, isKts),
            availableKnownRepositories = availableKnownRepositories,
            packageSearchModel = model,
            compatiblePackageTypes = buildPackagesType {
                mavenPackages()
                when {
                    model.isKotlinJvmApplied -> gradlePackages {
                        mustBeRootPublication = true
                        variant {
                            mustHaveFilesAttribute = false
                            javaApi()
                            javaRuntime()
                            libraryElements("jar")
                        }
                    }

                    model.isKotlinAndroidApplied -> {
                        gradlePackages {
                            mustBeRootPublication = true
                            variant {
                                mustHaveFilesAttribute = false
                                javaApi()
                                javaRuntime()
                                libraryElements("aar")
                            }
                        }
                        gradlePackages {
                            mustBeRootPublication = true
                            variant {
                                mustHaveFilesAttribute = false
                                javaApi()
                                javaRuntime()
                                libraryElements("jar")
                            }
                        }
                    }

                    else -> gradlePackages {
                        mustBeRootPublication = true
                    }
                }
            }
        )
    }

    override fun buildModule(
        context: PackageSearchModuleBuilderContext,
        nativeModule: NativeModule,
    ): Flow<PackageSearchModule?> = when {
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
                nativeModule.toPackageSearch(context, model, buildFile)
            }
    }


    private suspend fun Module.getDeclaredDependencies(
        context: PackageSearchModuleBuilderContext,
        isKts: Boolean,
    ): List<PackageSearchDeclaredDependency> {
        val declaredDependencies = readAction {
            DependencyModifierService.getInstance(context.project).declaredDependencies(this)
        }

        val remoteInfo = declaredDependencies.asSequence()
            .filter { it.coordinates.artifactId != null && it.coordinates.groupId != null }.map { it.packageId }
            .distinct().map { ApiPackage.hashPackageId(it) }.toSet().let { context.getPackageInfoByIdHashes(it) }

        return declaredDependencies.associateBy { it.packageId }.mapNotNull { (packageId, declaredDependency) ->
            PackageSearchDeclaredGradleDependency(
                id = packageId,
                declaredVersion = NormalizedVersion.from(declaredDependency.coordinates.version),
                latestStableVersion = remoteInfo[packageId]?.versions
                    ?.asSequence()
                    ?.map { it.normalized }
                    ?.filter { it.isStable }
                    ?.maxOrNull()
                    ?: NormalizedVersion.Missing,
                latestVersion = remoteInfo[packageId]?.versions
                    ?.asSequence()
                    ?.map { it.normalized }
                    ?.maxOrNull()
                    ?: NormalizedVersion.Missing,
                remoteInfo = remoteInfo[packageId],
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

    private suspend fun NativeModule.getDeclaredKnownRepositories(
        context: PackageSearchModuleBuilderContext,
    ): Map<String, ApiRepository> {
        val declaredDependencies = readAction {
            DependencyModifierService.getInstance(project).declaredRepositories(this)
        }.mapNotNull { it.id }
        return context.knownRepositories.filterKeys { it in declaredDependencies }
    }

}