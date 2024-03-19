@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.externalSystem.DependencyModifierService
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext


context(EditModuleContext)
internal fun validateContextType(): DependencyModifierService {
    require(data is DependencyModifierService) {
        "Context must be EditMavenModuleContext"
    }
    return data as DependencyModifierService
}

context(EditModuleContext)
val modifier
    get() = validateContextType()