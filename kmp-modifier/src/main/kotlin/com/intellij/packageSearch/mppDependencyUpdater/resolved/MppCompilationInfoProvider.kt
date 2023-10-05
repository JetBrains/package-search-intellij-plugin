// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.resolved

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

object MppCompilationInfoProvider {
    fun sourceSetsMap(
        project: Project,
        projectPath: Path,
    ): Flow<Map<String, Set<MppCompilationInfoModel.Compilation>>> =
        project.service<MppDataNodeProcessor.Cache>()
            .state
            .mapNotNull {
                it[projectPath]?.compilationsBySourceSetName
            }
}