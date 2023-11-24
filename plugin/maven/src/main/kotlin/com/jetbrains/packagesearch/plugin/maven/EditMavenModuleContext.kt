package com.jetbrains.packagesearch.plugin.maven

import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import org.jetbrains.idea.maven.dsl.MavenDependencyModificator

internal class EditMavenModuleContext(override val data: MavenDependencyModificator) : EditModuleContext