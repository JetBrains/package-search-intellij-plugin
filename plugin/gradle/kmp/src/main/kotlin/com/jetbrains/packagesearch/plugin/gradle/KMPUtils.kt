@file:OptIn(ExperimentalContracts::class)

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.intellij.packageSearch.mppDependencyUpdater.MppDependency
import com.intellij.packageSearch.mppDependencyUpdater.MppDependencyModifier
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchProjectCachesService
import com.jetbrains.packagesearch.plugin.core.utils.icon
import com.jetbrains.packagesearch.plugin.core.utils.parseAttributesFromRawStrings
import com.jetbrains.packagesearch.plugin.gradle.utils.GradleDependencyModelCacheEntry
import com.jetbrains.packagesearch.plugin.gradle.utils.getDeclaredDependencies
import com.jetbrains.packagesearch.plugin.gradle.utils.toGradleDependencyModel
import java.nio.file.Path
import korlibs.crypto.sha512
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.dizitart.kno2.filters.eq
import org.dizitart.no2.collection.UpdateOptions
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.kotlinMultiplatform
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

fun Set<MppCompilationInfoModel.Compilation>.buildAttributes(): List<PackageSearchModuleVariant.Attribute> {
    val rawStrings = this@buildAttributes.mapNotNull {
        when (it) {
            is MppCompilationInfoModel.Js -> when (it.compiler) {
                MppCompilationInfoModel.Js.Compiler.IR -> "jsIr"
                MppCompilationInfoModel.Js.Compiler.LEGACY -> "jsLegacy"
            }

            is MppCompilationInfoModel.Native -> it.target
            MppCompilationInfoModel.Common -> null
            else -> it.platformId
        }
    }.toSet()

    return rawStrings.parseAttributesFromRawStrings()
}

internal fun PackageSearchModuleVariant.Attribute.flatAttributesNames(): Set<String> {
    return when (this) {
        is PackageSearchModuleVariant.Attribute.StringAttribute -> setOf(value)
        is PackageSearchModuleVariant.Attribute.NestedAttribute -> children.flatMap { it.flatAttributesNames() }.toSet()
    }
}

fun List<PackageSearchModuleVariant.Attribute>.mergeAttributes() =
    flatMap { it.flatAttributesNames() }
        .toSet()
        .parseAttributesFromRawStrings()

operator fun Set<String>.contains(attribute: PackageSearchModuleVariant.Attribute.NestedAttribute): Boolean =
    attribute.flatten().all { it in this }

fun PackageSearchModuleVariant.Attribute.NestedAttribute.flatten() = buildSet {
    val queue = mutableListOf(this@flatten)
    while (queue.isNotEmpty()) {
        val next = queue.removeFirst()
        addAll(next.children.filterIsInstance<PackageSearchModuleVariant.Attribute.StringAttribute>().map { it.value })
        queue.addAll(next.children.filterIsInstance<PackageSearchModuleVariant.Attribute.NestedAttribute>())
    }
}

fun <K, V> Map<K, V?>.filterNotNullValues(): Map<K, V> = buildMap {
    for ((key, value) in this@filterNotNullValues) {
        if (value != null) {
            put(key, value)
        }
    }
}

fun List<PackageSearchGradleDeclaredPackage>.asKmpVariantDependencies() =
    map {
        PackageSearchKotlinMultiplatformDeclaredDependency.Maven(
            id = it.id,
            declaredVersion = it.declaredVersion,
            remoteInfo = it.remoteInfo,
            declarationIndexes = it.declarationIndexes,
            groupId = it.groupId,
            artifactId = it.artifactId,
            variantName = PackageSearchKotlinMultiplatformVariant.DependenciesBlock.NAME,
            configuration = it.configuration,
            icon = it.icon
        )
    }

val Map<String, PackageSearchKotlinMultiplatformVariant>.commonMain: PackageSearchKotlinMultiplatformVariant.SourceSet
    get() = get("commonMain") as PackageSearchKotlinMultiplatformVariant.SourceSet

val Map<String, PackageSearchKotlinMultiplatformVariant>.dependenciesBlock: PackageSearchKotlinMultiplatformVariant.DependenciesBlock
    get() = getValue(PackageSearchKotlinMultiplatformVariant.DependenciesBlock.NAME) as PackageSearchKotlinMultiplatformVariant.DependenciesBlock

val Map<String, PackageSearchKotlinMultiplatformVariant>.cocoapods: PackageSearchKotlinMultiplatformVariant.Cocoapods?
    get() = get(PackageSearchKotlinMultiplatformVariant.Cocoapods.NAME) as PackageSearchKotlinMultiplatformVariant.Cocoapods?

internal fun validateKMPDeclaredPackageType(declaredPackage: PackageSearchDeclaredPackage) {
    contract {
        returns() implies (declaredPackage is PackageSearchKotlinMultiplatformDeclaredDependency)
    }
    require(declaredPackage is PackageSearchKotlinMultiplatformDeclaredDependency) {
        "Declared package $declaredPackage is not a KMP dependency"
    }
}

internal fun EditModuleContext.validate(): EditKMPModuleContextData {
    require(data is EditKMPModuleContextData) { "Context is not a KMP context" }
    return data as EditKMPModuleContextData
}

val EditModuleContext.kmpData
    get() = validate()

fun PackageSearchKotlinMultiplatformDeclaredDependency.Maven.toMPPDependency() =
    MppDependency.Maven(
        groupId = groupId,
        artifactId = artifactId,
        version = declaredVersion?.versionName,
        configuration = configuration,
    )


suspend fun Module.getKMPVariants(
    context: PackageSearchModuleBuilderContext,
    compilationModel: Map<String, Set<MppCompilationInfoModel.Compilation>>,
    buildFilePath: Path?,
    availableScopes: List<String>,
): List<PackageSearchKotlinMultiplatformVariant> = coroutineScope {
    if (buildFilePath == null) return@coroutineScope emptyList()

    val dependenciesBlockVariant = async {
        val declaredDependencies = getDeclaredDependencies(context, buildFilePath)
        PackageSearchKotlinMultiplatformVariant.DependenciesBlock(
            declaredDependencies = declaredDependencies.asKmpVariantDependencies(),
            compatiblePackageTypes = buildPackageTypes {
                mavenPackages()
                gradlePackages {
                    isRootPublication = true
                }
            },
            availableScopes = availableScopes,
            defaultScope = "implementation".takeIf { it in availableScopes }
                ?: declaredDependencies.map { it.configuration }
                    .groupBy { it }
                    .mapValues { it.value.count() }
                    .entries
                    .maxByOrNull { it.value }
                    ?.key
                ?: availableScopes.first()
        )
    }

    val rawDeclaredSourceSetDependencies = getDependenciesBySourceSet(buildFilePath)

    val packageIds = rawDeclaredSourceSetDependencies
        .values
        .asSequence()
        .flatten()
        .distinct()
        .map { it.packageId }

    val dependencyInfo = context.getPackageInfoByIdHashes(packageIds.map { ApiPackage.hashPackageId(it) }.toSet())

    val declaredSourceSetDependencies =
        rawDeclaredSourceSetDependencies
            .mapValues { (sourceSetName, dependencies) ->
                dependencies.map { artifactModel ->
                    PackageSearchKotlinMultiplatformDeclaredDependency.Maven(
                        id = artifactModel.packageId,
                        declaredVersion = artifactModel.version?.let { NormalizedVersion.from(it) },
                        remoteInfo = dependencyInfo[artifactModel.packageId] as? ApiMavenPackage,
                        declarationIndexes = artifactModel.indexes,
                        groupId = artifactModel.groupId,
                        artifactId = artifactModel.artifactId,
                        variantName = sourceSetName,
                        configuration = artifactModel.configuration,
                        icon = dependencyInfo[artifactModel.packageId]?.icon
                            ?: IconProvider.Icons.GRADLE
                    )
                }
            }
    val sourceSetVariants = compilationModel
        .mapKeys { it.key }
        .map { (sourceSetName, compilationTargets) ->
            PackageSearchKotlinMultiplatformVariant.SourceSet(
                name = sourceSetName,
                declaredDependencies = declaredSourceSetDependencies[sourceSetName] ?: emptyList(),
                attributes = compilationTargets.buildAttributes(),
                compatiblePackageTypes = buildPackageTypes {
                    gradlePackages {
                        kotlinMultiplatform {
                            compilationTargets.forEach { compilationTarget ->
                                when {
                                    compilationTarget is MppCompilationInfoModel.Js -> when (compilationTarget.compiler) {
                                        MppCompilationInfoModel.Js.Compiler.IR -> jsIr()
                                        MppCompilationInfoModel.Js.Compiler.LEGACY -> jsLegacy()
                                    }

                                    compilationTarget is MppCompilationInfoModel.Native -> native(compilationTarget.target)
                                    compilationTarget == MppCompilationInfoModel.Wasm -> wasm()
                                }
                            }
                            when {
                                MppCompilationInfoModel.Android in compilationTargets -> android()
                                MppCompilationInfoModel.Jvm in compilationTargets -> jvm()
                            }
                        }
                    }
                },
                compilerTargets = compilationTargets
            )
        }

    sourceSetVariants + dependenciesBlockVariant.await()
}

@Serializable
data class GradleKMPDependencyModelCacheEntry(
    @SerialName("_id") val id: Long? = null,
    val buildFile: String,
    val buildFileSha: String,
    val dependencies: Map<String, List<GradleDependencyModel>>,
)

@Service(Level.PROJECT)
class GradleKMPCacheService(project: Project) : Disposable {
    val kmpDependencyRepository =
        project.PackageSearchProjectCachesService.getRepository<GradleKMPDependencyModelCacheEntry>("gradle-kmp-dependencies")

    override fun dispose() = kmpDependencyRepository.close()
}

private suspend fun Module.getDependenciesBySourceSet(
    buildFilePath: Path
): Map<String, List<GradleDependencyModel>> {
    val vf = buildFilePath.refreshAndFindVirtualFile() ?: return emptyMap()

    val buildFileHash = vf.contentsToByteArray().sha512().hex

    val entry = withContext(Dispatchers.IO) {
        project.service<GradleKMPCacheService>()
            .kmpDependencyRepository
            .find(GradleDependencyModelCacheEntry::buildFile eq buildFilePath.absolutePathString())
            .singleOrNull()
    }

    if (entry?.buildFileSha == buildFileHash) return entry.dependencies

    val filteredDependenciesBySourceSet =
        MppDependencyModifier.dependenciesBySourceSet(this)
            ?.filterNotNullValues()
            ?.mapValues { readAction { it.value.artifacts().map { it.toGradleDependencyModel() } }.distinct() }
            ?: emptyMap()

    project.service<GradleKMPCacheService>()
        .kmpDependencyRepository
        .update(
            /* filter = */ GradleDependencyModelCacheEntry::buildFile eq buildFilePath.absolutePathString(),
            /* update = */ GradleKMPDependencyModelCacheEntry(
                buildFile = buildFilePath.absolutePathString(),
                buildFileSha = buildFileHash,
                dependencies = filteredDependenciesBySourceSet
            ),
            /* updateOptions = */ UpdateOptions.updateOptions(true)
        )

    return filteredDependenciesBySourceSet
}