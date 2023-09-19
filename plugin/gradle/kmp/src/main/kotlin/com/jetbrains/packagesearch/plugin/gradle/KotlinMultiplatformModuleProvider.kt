@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.registry.Registry
import com.intellij.packageSearch.mppDependencyUpdater.MppDependencyModifier
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Android
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Js
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Jvm
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel.Native
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoProvider
import com.jetbrains.packagesearch.plugin.core.data.IconProvider.Icons
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant.Attribute
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.utils.getIcon
import com.jetbrains.packagesearch.plugin.gradle.utils.commonConfigurations
import com.jetbrains.packagesearch.plugin.gradle.utils.getDeclaredDependencies
import com.jetbrains.packagesearch.plugin.gradle.utils.toGradleDependencyModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.kotlinMultiplatform
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

object KMPAttributes {
    val iosArm64 = Attribute.StringAttribute("ios_arm64")
    val iosX64 = Attribute.StringAttribute("ios_x64")
    val ios = Attribute.NestedAttribute("iOS", listOf(iosArm64, iosX64))

    val macosX64 = Attribute.StringAttribute("macos_x64")
    val macosArm64 = Attribute.StringAttribute("macos_arm64")
    val macos = Attribute.NestedAttribute("macOs", listOf(macosX64, macosArm64))

    val watchosArm32 = Attribute.StringAttribute("watchos_arm32")
    val watchosArm64 = Attribute.StringAttribute("watchos_arm64")
    val watchosX64 = Attribute.StringAttribute("watchos_x64")
    val watchosDevice = Attribute.NestedAttribute("watchOs", listOf(watchosArm32, watchosArm64))
    val watchos = Attribute.NestedAttribute("watchOs", listOf(watchosDevice, watchosX64))

    val tvosArm64 = Attribute.StringAttribute("tvos_arm64")
    val tvosX64 = Attribute.StringAttribute("tvos_x64")
    val tvos = Attribute.NestedAttribute("tvOs", listOf(tvosArm64, tvosX64))

    val apple = Attribute.NestedAttribute("Apple", listOf(ios, macos, watchos, tvos))

    val jsLegacy = Attribute.StringAttribute("jsLegacy")
    val jsIr = Attribute.StringAttribute("jsIr")
    val js = Attribute.NestedAttribute("JavaScript", listOf(jsLegacy, jsIr))

    val linuxArm64 = Attribute.StringAttribute("linuxArm64")
    val linuxX64 = Attribute.StringAttribute("linuxX64")
    val linux = Attribute.NestedAttribute("Linux", listOf(linuxArm64, linuxX64))

    val android = Attribute.StringAttribute("android")

    val androidArm32 = Attribute.StringAttribute("androidArm32")
    val androidArm64 = Attribute.StringAttribute("androidArm64")
    val androidX64 = Attribute.StringAttribute("androidX64")
    val androidX86 = Attribute.StringAttribute("androidX86")
    val androidNative =
        Attribute.NestedAttribute("Android Native", listOf(androidArm32, androidArm64, androidX64, androidX86))

}

fun Set<MppCompilationInfoModel.Compilation>.buildAttributes() = buildList {
    val rawStrings = this@buildAttributes.mapNotNull {
        when (it) {
            is Js -> when (it.compiler) {
                Js.Compiler.IR -> "jsIr"
                Js.Compiler.LEGACY -> "jsLegacy"
            }

            is Native -> it.target
            MppCompilationInfoModel.Common -> null
            else -> it.platformId
        }
    }
        .toMutableSet()

    val hasIos = KMPAttributes.ios in rawStrings
    val hasMacOs = KMPAttributes.macos in rawStrings
    val hasTvOs = KMPAttributes.tvos in rawStrings
    val hasWatchOs = KMPAttributes.watchos in rawStrings

    val hasApple = hasIos && hasMacOs && hasTvOs && hasWatchOs

    val hasJs = KMPAttributes.js in rawStrings
    val hasAndroidNative = KMPAttributes.androidNative in rawStrings
    val hasLinux = KMPAttributes.linux in rawStrings
    when {
        hasApple -> {
            add(KMPAttributes.apple)
            rawStrings.removeAll(KMPAttributes.apple.flatten())
        }

        else -> {
            if (hasIos) {
                add(KMPAttributes.ios)
                rawStrings.removeAll(KMPAttributes.ios.flatten())
            }
            if (hasMacOs) {
                add(KMPAttributes.macos)
                rawStrings.removeAll(KMPAttributes.macos.flatten())
            }
            if (hasTvOs) {
                add(KMPAttributes.tvos)
                rawStrings.removeAll(KMPAttributes.tvos.flatten())
            }
            if (hasWatchOs) {
                add(KMPAttributes.watchos)
                rawStrings.removeAll(KMPAttributes.watchos.flatten())
            }
        }
    }
    if (hasJs) {
        add(KMPAttributes.js)
        rawStrings.removeAll(KMPAttributes.js.flatten())
    }
    if (hasAndroidNative) {
        add(KMPAttributes.androidNative)
        rawStrings.removeAll(KMPAttributes.androidNative.flatten())
    }
    if (hasLinux) {
        add(KMPAttributes.linux)
        rawStrings.removeAll(KMPAttributes.linux.flatten())
    }
    addAll(rawStrings.map { Attribute.StringAttribute(it) })
}

operator fun Set<String>.contains(attribute: Attribute.NestedAttribute): Boolean =
    attribute.flatten().all { it in this }

class KotlinMultiplatformModuleProvider : BaseGradleModuleProvider() {

    override suspend fun FlowCollector<PackageSearchModuleData?>.transform(
        module: Module,
        context: PackageSearchModuleBuilderContext,
        model: PackageSearchGradleModel,
    ) {
        if (model.isKotlinMultiplatformApplied)
            MppCompilationInfoProvider.sourceSetsMap(context.project, model.projectDir)
                .collect { compilationModel ->
                    val pkgsModule = PackageSearchKotlinMultiplatformModule(
                        name = model.projectName,
                        identity = PackageSearchModule.Identity("gradle", model.projectIdentityPath),
                        buildFilePath = model.buildFilePath,
                        declaredKnownRepositories = context.knownRepositories - DependencyModifierService
                            .getInstance(context.project)
                            .declaredRepositories(module)
                            .mapNotNull { it.id }
                            .toSet(),
                        defaultScope = "implementation",
                        availableScopes = commonConfigurations.toList(),
                        variants = module.getKMPVariants(context = context, compilationModel = compilationModel)
                            .associateBy { it.name },
                        packageSearchModel = model,
                        availableKnownRepositories = context.knownRepositories
                    )
                    emit(
                        PackageSearchModuleData(
                            module = pkgsModule,
                            dependencyManager = PackageSearchKotlinMultiplatformDependencyManager(
                                model,
                                pkgsModule,
                                module
                            )
                        )
                    )
                }

    }

    suspend fun Module.getKMPVariants(
        compilationModel: Map<String, Set<MppCompilationInfoModel.Compilation>>,
        context: PackageSearchModuleBuilderContext,
    ): List<PackageSearchKotlinMultiplatformVariant> = coroutineScope {
        val dependenciesBlockVariant = async {
            PackageSearchKotlinMultiplatformVariant.DependenciesBlock(
                declaredDependencies = getDeclaredDependencies(context).asKmpVariantDependencies(),
                compatiblePackageTypes = buildPackageTypes {
                    mavenPackages()
                    gradlePackages {
                        mustBeRootPublication = true
                    }
                }
            )
        }

        val rawDeclaredSourceSetDependencies = MppDependencyModifier
            .dependenciesBySourceSet(this@getKMPVariants)
            ?.mapNotNull { (key, value) ->
                value?.let {
                    key to readAction {
                        it.artifacts().map {
                            it.toGradleDependencyModel()
                        }
                    }
                }
            }
            ?.toMap()
            ?: emptyMap()

        val packageIds = rawDeclaredSourceSetDependencies
            .values
            .asSequence()
            .flatten()
            .distinct()
            .map { it.packageId }

        val isSonatype = Registry.`is`("org.jetbrains.packagesearch.sonatype")
        val dependencyInfo = if (!isSonatype) {
            context.getPackageInfoByIdHashes(packageIds.map { ApiPackage.hashPackageId(it) }.toSet())
        } else {
            context.getPackageInfoByIds(packageIds.toSet())
        }

        val declaredSourceSetDependencies =
            rawDeclaredSourceSetDependencies
                .mapValues { (sourceSetName, dependencies) ->
                    dependencies.map { artifactModel ->
                        PackageSearchKotlinMultiplatformDeclaredDependency.Maven(
                            id = artifactModel.packageId,
                            declaredVersion = NormalizedVersion.from(artifactModel.version),
                            latestStableVersion = dependencyInfo[artifactModel.packageId]?.versions?.latestStable?.normalized
                                ?: NormalizedVersion.Missing,
                            latestVersion = dependencyInfo[artifactModel.packageId]?.versions?.latest?.normalized
                                ?: NormalizedVersion.Missing,
                            remoteInfo = dependencyInfo[artifactModel.packageId] as? ApiMavenPackage,
                            declarationIndexes = artifactModel.indexes,
                            groupId = artifactModel.groupId,
                            artifactId = artifactModel.artifactId,
                            variantName = sourceSetName,
                            configuration = artifactModel.configuration,
                            icon = dependencyInfo[artifactModel.packageId]?.getIcon(artifactModel.version)
                                ?: Icons.GRADLE
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
                        if (compilationTargets.singleOrNull() == Jvm) {
                            mavenPackages()
                        }
                        gradlePackages {
                            kotlinMultiplatform {
                                compilationTargets.forEach { compilationTarget ->
                                    when (compilationTarget) {
                                        is Js -> when (compilationTarget.compiler) {
                                            Js.Compiler.IR -> jsIr()
                                            Js.Compiler.LEGACY -> jsLegacy()
                                        }

                                        is Native -> native(compilationTarget.target)
                                        else -> {}
                                    }
                                }
                                when {
                                    Android in compilationTargets -> android()
                                    Jvm in compilationTargets -> jvm()
                                }
                            }
                        }
                    },
                    compilerTargets = compilationTargets
                )
            }

        sourceSetVariants + dependenciesBlockVariant.await()
    }
}

fun List<PackageSearchGradleDeclaredPackage>.asKmpVariantDependencies() =
    map {
        PackageSearchKotlinMultiplatformDeclaredDependency.Maven(
            id = it.id,
            declaredVersion = it.declaredVersion,
            latestStableVersion = it.latestStableVersion,
            latestVersion = it.latestVersion,
            remoteInfo = it.remoteInfo,
            declarationIndexes = it.declarationIndexes,
            groupId = it.groupId,
            artifactId = it.artifactId,
            variantName = PackageSearchKotlinMultiplatformVariant.DependenciesBlock.NAME,
            configuration = it.configuration,
            icon = it.icon
        )
    }

fun Attribute.NestedAttribute.flatten() = buildSet {
    val queue = mutableListOf(this@flatten)
    while (queue.isNotEmpty()) {
        val next = queue.removeFirst()
        addAll(next.children.filterIsInstance<Attribute.StringAttribute>().map { it.value })
        queue.addAll(next.children.filterIsInstance<Attribute.NestedAttribute>())
    }
}