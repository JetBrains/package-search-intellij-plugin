package com.jetbrains.packagesearch.plugin.core.utils

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant


data class KmpAttributesGroups(
    val displayName: String,
    val aggregationKeyword: String = displayName,
)

val KMP_ATTRIBUTES_GROUPS = listOf(
    KmpAttributesGroups("iOS"),
    KmpAttributesGroups("macOS"),
    KmpAttributesGroups("tvOS"),
    KmpAttributesGroups("watchOS"),
    KmpAttributesGroups("JS"),
    KmpAttributesGroups("JVM"),
    KmpAttributesGroups("Linux"),
    KmpAttributesGroups("Android"),
    KmpAttributesGroups("WASM"),
    KmpAttributesGroups("Windows", "mingw"),
)

fun Set<String>.parseAttributesFromRawStrings() = buildList {
    var queue = this@parseAttributesFromRawStrings.toList()
    for (attributeTitle in KMP_ATTRIBUTES_GROUPS) {
        val (targets, rest) = queue
            .partition { it.contains(attributeTitle.aggregationKeyword, true) }

        if (targets.isEmpty()) continue
        this.add(
            PackageSearchModuleVariant.Attribute.NestedAttribute(
                attributeTitle.displayName,
                targets.map { PackageSearchModuleVariant.Attribute.StringAttribute(it) })
        )
        queue = rest
    }
    addAll(queue.map { PackageSearchModuleVariant.Attribute.StringAttribute(it) })

}

