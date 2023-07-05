// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.resolved

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.alsoIfNull
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinMppGradleProjectResolverExtension
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform

private val LOG = logger<MppGradleProjectResolver>()

class MppGradleProjectResolver : KotlinMppGradleProjectResolverExtension {
  private fun KotlinMppGradleProjectResolver.Context.compilationToSourceSetMap(): Map<MppCompilationInfoModel.SourceSet, Set<MppCompilationInfoModel.Compilation>> =
    mppModel.targets.flatMap { target ->
      target.compilations.flatMap { compilation ->
        compilation.allSourceSets.map { sourceSet ->
          compilation.toCompilationModel()?.let { MppCompilationInfoModel.SourceSet(sourceSet.name) to it }
        }
      }
    }
      .filterNotNull()
      .groupBy { it.first }
      .mapValues { entry -> entry.value.map { it.second }.toSet() }

  private fun KotlinCompilation.toCompilationModel(): MppCompilationInfoModel.Compilation? =
    when (platform) {
      KotlinPlatform.COMMON -> MppCompilationInfoModel.Common
      KotlinPlatform.JVM -> MppCompilationInfoModel.Jvm
      KotlinPlatform.JS -> if (compilerArguments?.contains(MppCompilationInfoModel.Js.Compiler.IR.flag) == true) {
            MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.IR)
          } else {
            MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY)
          }
      KotlinPlatform.ANDROID -> MppCompilationInfoModel.Android
      KotlinPlatform.WASM -> MppCompilationInfoModel.Wasm
      KotlinPlatform.NATIVE -> nativeExtensions?.let { MppCompilationInfoModel.Native(it.konanTarget) }
                                .alsoIfNull { LOG.error("No native extensions found for the $name compilation") }
    }

  override fun afterResolveFinished(context: KotlinMppGradleProjectResolver.Context) {
    val model = MppCompilationInfoModel(context.moduleDataNode.data.linkedExternalProjectPath, context.compilationToSourceSetMap())
    context.moduleDataNode.createChild(MppDataNodeProcessor.Util.MPP_SOURCES_SETS_MAP_KEY, model)
    super.afterResolveFinished(context)
  }
}