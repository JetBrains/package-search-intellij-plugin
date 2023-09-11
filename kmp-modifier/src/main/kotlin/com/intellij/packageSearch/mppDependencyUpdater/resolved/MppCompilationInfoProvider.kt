// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.resolved

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.gradle.plugins.ide.eclipse.model.Project

object MppCompilationInfoProvider {
    fun sourceSetsMap(project: com.intellij.openapi.project.Project, projectPath: String): Flow<MppCompilationInfoModel> {
        return project.service<MppDataNodeProcessor.Cache>()
            .state
            .mapNotNull { it[projectPath] }
    }
}