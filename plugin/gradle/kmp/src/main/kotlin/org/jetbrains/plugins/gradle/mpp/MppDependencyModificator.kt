package org.jetbrains.plugins.gradle.mpp// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.util.PsiElementHolder
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import com.intellij.openapi.module.Module

sealed interface MppDependencyModificator {

  companion object {
    fun getInstance(project: Project): MppDependencyModificator = TODO("To implement")
  }

  suspend fun isAvailable(module: Module): Boolean

  suspend fun sourceSets(module: Module): List<SourceSetModel>?

  suspend fun dependenciesBySourceSet(module: Module): Map<String, DependenciesModel?>?

  suspend fun addDependency(
    module: Module,
    mppDependency: MppDependency,
    createIfMissing: Boolean = true,
  )

  suspend fun addDependencies(
    module: Module,
    mppDependencies: List<MppDependency>,
    createIfMissing: Boolean = true,
  )

  suspend fun removeDependency(
    module: Module,
    mppDependency: MppDependency,
  )

  suspend fun removeDependencies(
    module: Module,
    mppDependencies: List<MppDependency>,
  )

  suspend fun updateDependency(
    module: Module,
    oldMppDependency: MppDependency,
    newMppDependency: MppDependency,
    createIfMissing: Boolean = true,
  )

  suspend fun updateDependencies(
    module: Module,
    updates: List<Pair<MppDependency, MppDependency>>,
    createIfMissing: Boolean = true,
  )
}

interface SourceSetModel: PsiElementHolder {
  val name: String
}

data class MppDependency(
  val unifiedDependency: UnifiedDependency,
  val sourceSet: String
)

interface MppCompilationInfoProvider {
  // Get a map of which source set is consumed by which compilation
  fun sourceSetsMap(module: Module): Map<MppCompilationInfoModel.SourceSet, List<MppCompilationInfoModel.Compilation>>?
}

@Serializable
data class MppCompilationInfoModel(
  val projectDir: String,
  val compilationsBySourceSet: Map<SourceSet, List<Compilation>>
) {

  @Serializable
  data class SourceSet(
    val name: String,
  )

  @Serializable
  data class Compilation(
    val platformName: String,
  )
}