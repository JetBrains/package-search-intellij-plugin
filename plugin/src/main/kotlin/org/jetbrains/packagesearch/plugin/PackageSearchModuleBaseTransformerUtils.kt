package org.jetbrains.packagesearch.plugin

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleTransformer
import org.jetbrains.packagesearch.plugin.core.utils.extensionsFlow

object PackageSearchModuleBaseTransformerUtils {

    val extensionPointName =
        ExtensionPointName.create<PackageSearchModuleTransformer>("org.jetbrains.packagesearch.moduleTransformer")

    val extensionsFlow
        get() = extensionPointName.extensionsFlow()
}