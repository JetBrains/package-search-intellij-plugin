// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.dsl

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.android.tools.idea.gradle.dsl.model.GradleBlockModelMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.intellij.packageSearch.mppDependencyUpdater.dsl.elements.KotlinDslElement
import com.intellij.packageSearch.mppDependencyUpdater.dsl.models.KotlinDslModel

class KotlinDslProvider : GradleBlockModelMap.BlockModelProvider<GradleBuildModel, GradleDslFile> {
  private val KOTLIN_ELEMENTS = mapOf(
    "kotlin" to KotlinDslElement.KOTLIN
  )

  private val KOTLIN_MODELS = listOf<GradleBlockModelMap.BlockModelBuilder<*, GradleDslFile>>(
    KotlinDslModel::class.java from {
      KotlinDslModel(it.ensurePropertyElement(KotlinDslElement.KOTLIN))
    },
  )

  override fun getParentClass() = GradleBuildModel::class.java

  override fun availableModels(): List<GradleBlockModelMap.BlockModelBuilder<*, GradleDslFile>> {
    return KOTLIN_MODELS
  }

  override fun elementsMap(): Map<String, PropertiesElementDescription<*>> {
    return KOTLIN_ELEMENTS
  }
}

private infix fun <M, P> Class<M>.from(action: (P) -> M): GradleBlockModelMap.BlockModelBuilder<M, P> where M : GradleDslModel, P : GradlePropertiesDslElement {
  return object : GradleBlockModelMap.BlockModelBuilder<M, P> {
    override fun modelClass() = this@from
    override fun create(p: P): M = action(p)
  }
}