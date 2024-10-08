@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.externalSystem.DependencyModifierService
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredRepository
import kotlin.contracts.contract


internal fun EditModuleContext.validateContextType(): DependencyModifierService {
    require(data is DependencyModifierService) {
        "Context must be EditMavenModuleContext"
    }
    return data as DependencyModifierService
}

val EditModuleContext.modifier
    get() = validateContextType()