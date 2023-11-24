package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.externalSystem.DependencyModifierService
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext

class EditGradleModuleContext(override val data: DependencyModifierService) : EditModuleContext