// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.dsl

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.android.tools.idea.gradle.dsl.model.BlockModelBuilder
import com.android.tools.idea.gradle.dsl.model.BlockModelProvider
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.intellij.packageSearch.mppDependencyUpdater.dsl.elements.KotlinDslElement
import com.intellij.packageSearch.mppDependencyUpdater.dsl.models.KotlinDslModel

class KotlinDslProvider : BlockModelProvider<GradleBuildModel, GradleDslFile> {
  private val KOTLIN_ELEMENTS = mapOf(
    "kotlin" to KotlinDslElement.KOTLIN
  )

  private val KOTLIN_MODELS = listOf<BlockModelBuilder<*, GradleDslFile>>(
    KotlinDslModel::class.java from {
      KotlinDslModel(it.ensurePropertyElement(KotlinDslElement.KOTLIN))
    },
  )

  override val parentClass: Class<GradleBuildModel>
    get() = GradleBuildModel::class.java
  override val parentDslClass: Class<GradleDslFile>
    get() = GradleDslFile::class.java

  override fun availableModels(kind: GradleDslNameConverter.Kind): List<BlockModelBuilder<*, GradleDslFile>> =
    KOTLIN_MODELS

  override fun elementsMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>> =
    KOTLIN_ELEMENTS
}

private infix fun <M, P> Class<M>.from(action: (P) -> M): BlockModelBuilder<M, P> where M : GradleDslModel, P : GradlePropertiesDslElement {
  return object : BlockModelBuilder<M, P> {
    override fun modelClass() = this@from
    override fun create(p: P): M = action(p)
  }
}