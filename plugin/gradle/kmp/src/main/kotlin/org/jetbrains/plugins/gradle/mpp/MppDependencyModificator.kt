package org.jetbrains.plugins.gradle.mpp// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.util.PsiElementHolder
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import com.intellij.openapi.module.Module
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

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
    sourceSet: String,
    createIfMissing: Boolean = true,
  )

  suspend fun addDependencies(
    module: Module,
    mppDependencies: List<MppDependency>,
    createIfMissing: Boolean = true,
  )

  suspend fun removeDependency(
    module: Module,
    sourceSet: String,
    mppDependency: MppDependency,
  )

  suspend fun removeDependencies(
    module: Module,
    mppDependencies: List<MppDependency>,
  )

  suspend fun removeDependencies(
    module: Module,
    data: List<MppModifierUpdateData>,
    createIfMissing: Boolean = true // this is actually missing
  )

  suspend fun updateDependency(
    module: Module,
    data: MppModifierUpdateData,
    createIfMissing: Boolean = true
  )

  suspend fun updateDependencies(
    module: Module,
    data: List<MppModifierUpdateData>,
    createIfMissing: Boolean = true
  )
}

interface SourceSetModel: PsiElementHolder {
  val name: String
}

data class MppModifierUpdateData(
  val sourceSet: String,
  val oldDescriptor: MppDependency,
  val newDescriptor: MppDependency
)

sealed interface MppDependency {
  val version: String?

  data class Maven(
    val groupId: String,
    val artifactId: String,
    override val version: String?,
    val configuration: String
  ): MppDependency

  data class Npm(
    val name: String,
    override val version: String?,
    val configuration: String
  ): MppDependency

  data class Cocoapods(
    val name: String,
    override val version: String?
  ): MppDependency
}

interface MppCompilationInfoProvider {

  companion object {
    fun getInstance(project: Project): MppCompilationInfoProvider = TODO()
    fun sourceSetsMap(module: Module) =
      getInstance(module.project).sourceSetsMap(module)
  }

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
sealed interface Compilation {
  val platformId: String

  @Serializable
  object Jvm : Compilation {
    override val platformId = "jvm"
  }

  @Serializable
  object Android : Compilation {
    override val platformId = "android"
  }

  @Serializable
  data class Js(val compiler: Compiler) : Compilation {
    enum class Compiler {
      IR, LEGACY
    }

    override val platformId = "js"
  }

  @Serializable
  data class Native(val target: String) : Compilation {
    override val platformId = "native"
  }
}
}