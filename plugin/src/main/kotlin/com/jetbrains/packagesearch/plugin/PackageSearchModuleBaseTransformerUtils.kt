package com.jetbrains.packagesearch.plugin

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleProvider
import com.jetbrains.packagesearch.plugin.core.utils.extensionsFlow

object PackageSearchModuleBaseTransformerUtils {

    val extensionPointName =
        ExtensionPointName.create<PackageSearchModuleProvider>("com.intellij.packagesearch.moduleProvider")

    val extensionsFlow
        get() = extensionPointName.extensionsFlow()

}