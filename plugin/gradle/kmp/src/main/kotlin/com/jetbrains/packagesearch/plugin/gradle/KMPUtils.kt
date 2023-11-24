package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.packageSearch.mppDependencyUpdater.MppDependency
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import kotlin.contracts.contract

fun Set<MppCompilationInfoModel.Compilation>.buildAttributes() = buildList {
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
    addAll(rawStrings.map { PackageSearchModuleVariant.Attribute.StringAttribute(it) })
}

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

context(EditModuleContext)
internal fun validateContextType(): EditKMPModuleContextData {
    require(data is EditKMPModuleContextData) { "Context is not a KMP context" }
    return data as EditKMPModuleContextData
}

context(EditModuleContext)
val kmpData
    get() = validateContextType()

fun PackageSearchKotlinMultiplatformDeclaredDependency.Maven.toMPPDependency() =
    MppDependency.Maven(
        groupId = groupId,
        artifactId = artifactId,
        version = declaredVersion?.versionName,
        configuration = configuration,
    )