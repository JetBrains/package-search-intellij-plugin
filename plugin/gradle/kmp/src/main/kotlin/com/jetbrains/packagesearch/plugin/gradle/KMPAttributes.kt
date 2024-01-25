package com.jetbrains.packagesearch.plugin.gradle


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
