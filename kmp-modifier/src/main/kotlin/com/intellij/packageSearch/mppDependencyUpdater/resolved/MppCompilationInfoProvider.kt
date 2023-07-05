// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.resolved

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories

object MppCompilationInfoProvider {
  // Get a map of which source set is consumed by which compilation
  fun sourceSetsMap(module: Module): Map<MppCompilationInfoModel.SourceSet, Set<MppCompilationInfoModel.Compilation>>? {
    val projectPath = module.project.getBaseDirectories().firstOrNull()?.canonicalPath ?: return null

    val model = module.project
                  .service<MppDataNodeProcessor.Cache>()
                  .state[projectPath] ?: return null

    return model.compilationsBySourceSet
  }
}