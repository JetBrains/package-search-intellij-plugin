// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.resolved

import com.intellij.openapi.components.service
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart

object MppCompilationInfoProvider {
    fun sourceSetsMap(
        project: com.intellij.openapi.project.Project,
        projectPath: String,
    ) = project.service<MppDataNodeProcessor.Cache>()
        .state
        .mapNotNull { it[projectPath]?.compilationsBySourceSet }
        .onStart { emptyMap<MppCompilationInfoModel.SourceSet, Set<MppCompilationInfoModel.Compilation>>() }
}