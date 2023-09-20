package com.jetbrains.packagesearch.plugin.gradle

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant

object KMPAttributes {
    val iosArm64 = PackageSearchModuleVariant.Attribute.StringAttribute("ios_arm64")
    val iosX64 = PackageSearchModuleVariant.Attribute.StringAttribute("ios_x64")
    val ios = PackageSearchModuleVariant.Attribute.NestedAttribute("iOS", listOf(iosArm64, iosX64))

    val macosX64 = PackageSearchModuleVariant.Attribute.StringAttribute("macos_x64")
    val macosArm64 = PackageSearchModuleVariant.Attribute.StringAttribute("macos_arm64")
    val macos = PackageSearchModuleVariant.Attribute.NestedAttribute("macOs", listOf(macosX64, macosArm64))

    val watchosArm32 = PackageSearchModuleVariant.Attribute.StringAttribute("watchos_arm32")
    val watchosArm64 = PackageSearchModuleVariant.Attribute.StringAttribute("watchos_arm64")
    val watchosX64 = PackageSearchModuleVariant.Attribute.StringAttribute("watchos_x64")
    val watchosDevice =
        PackageSearchModuleVariant.Attribute.NestedAttribute("watchOs", listOf(watchosArm32, watchosArm64))
    val watchos = PackageSearchModuleVariant.Attribute.NestedAttribute("watchOs", listOf(watchosDevice, watchosX64))

    val tvosArm64 = PackageSearchModuleVariant.Attribute.StringAttribute("tvos_arm64")
    val tvosX64 = PackageSearchModuleVariant.Attribute.StringAttribute("tvos_x64")
    val tvos = PackageSearchModuleVariant.Attribute.NestedAttribute("tvOs", listOf(tvosArm64, tvosX64))

    val apple = PackageSearchModuleVariant.Attribute.NestedAttribute("Apple", listOf(ios, macos, watchos, tvos))

    val jsLegacy = PackageSearchModuleVariant.Attribute.StringAttribute("jsLegacy")
    val jsIr = PackageSearchModuleVariant.Attribute.StringAttribute("jsIr")
    val js = PackageSearchModuleVariant.Attribute.NestedAttribute("JavaScript", listOf(jsLegacy, jsIr))

    val linuxArm64 = PackageSearchModuleVariant.Attribute.StringAttribute("linuxArm64")
    val linuxX64 = PackageSearchModuleVariant.Attribute.StringAttribute("linuxX64")
    val linux = PackageSearchModuleVariant.Attribute.NestedAttribute("Linux", listOf(linuxArm64, linuxX64))

    val android = PackageSearchModuleVariant.Attribute.StringAttribute("android")

    val androidArm32 = PackageSearchModuleVariant.Attribute.StringAttribute("androidArm32")
    val androidArm64 = PackageSearchModuleVariant.Attribute.StringAttribute("androidArm64")
    val androidX64 = PackageSearchModuleVariant.Attribute.StringAttribute("androidX64")
    val androidX86 = PackageSearchModuleVariant.Attribute.StringAttribute("androidX86")
    val androidNative =
        PackageSearchModuleVariant.Attribute.NestedAttribute(
            "Android Native",
            listOf(androidArm32, androidArm64, androidX64, androidX86)
        )

}