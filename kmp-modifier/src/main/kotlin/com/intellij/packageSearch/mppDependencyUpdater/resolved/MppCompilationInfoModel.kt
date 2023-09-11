// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.resolved

import kotlinx.serialization.Serializable

@Serializable
data class MppCompilationInfoModel(
  val projectDir: String,
  val compilationsBySourceSet: Map<SourceSet, Set<Compilation>>
) {

  @Serializable
  data class SourceSet(
    val name: String,
  )

  @Serializable
  sealed interface Compilation {
    val platformId: String
  }

  @Serializable
  data object Jvm : Compilation {
    override val platformId = "jvm"
  }

  @Serializable
  data object Android : Compilation {
    override val platformId = "android"
  }

  @Serializable
  data object Wasm: Compilation {
    override val platformId = "wasm"
  }

  @Serializable
  data class Js(val compiler: Compiler): Compilation {
    enum class Compiler(val flag: String) {
      IR("-Xir-only"), LEGACY("")
    }

    override val platformId = "js"
  }

  @Serializable
  data class Native(val target: String): Compilation {
    override val platformId = "native"
  }

  /**
    Compatibility with NMPP (see [org.jetbrains.kotlin.idea.projectModel.KotlinPlatform.COMMON]
  **/
  @Serializable
  data object Common : Compilation {
    override val platformId = "common"
  }
}