package com.jetbrains.packagesearch.plugin

import com.intellij.openapi.util.registry.Registry

object FeatureFlags {

    val useDebugLogging: Boolean
        get() = Registry.`is`("packagesearch.plugin.debug.logging", false)

}