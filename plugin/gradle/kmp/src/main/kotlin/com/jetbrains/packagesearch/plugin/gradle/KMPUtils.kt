package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.packageSearch.mppDependencyUpdater.MppDependency
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import kotlin.contracts.contract

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

    return buildAttributesFromRawStrings(rawStrings)
}

fun buildAttributesFromRawStrings(rawStrings: Set<String>) = buildList {
    var queue = rawStrings.toList()
    for (attributeTitle in KMP_ATTRIBUTES_GROUPS) {
        val (targets, rest) = queue
            .partition { it.contains(attributeTitle.aggregationKeyword, true) }

        if (targets.isEmpty()) continue
        add(
            PackageSearchModuleVariant.Attribute.NestedAttribute(
                attributeTitle.displayName,
                targets.map { PackageSearchModuleVariant.Attribute.StringAttribute(it) })
        )
        queue = rest
    }
    addAll(queue.map { PackageSearchModuleVariant.Attribute.StringAttribute(it) })

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