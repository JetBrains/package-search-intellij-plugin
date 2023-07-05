// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.dsl.elements

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.DescribedGradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.google.common.collect.ImmutableMap

class KotlinDslElement(
  parent: GradleDslElement?,
  name: GradleNameElement,
) : GradleDslBlockElement(parent, name),
    DescribedGradlePropertiesDslElement<KotlinDslElement> {
  companion object {
    val KOTLIN = PropertiesElementDescription(
      "kotlin",
      KotlinDslElement::class.java,
    ) { parent: GradleDslElement, name: GradleNameElement -> KotlinDslElement(parent, name) }
  }

  override fun getDescription(): PropertiesElementDescription<KotlinDslElement> = KOTLIN

  @Suppress("UNCHECKED_CAST")
  private val CHILD_PROPERTIES_ELEMENTS_MAP = mapOf(
    "sourceSets" to SourceSetsElement.SOURCE_SETS as PropertiesElementDescription<GradlePropertiesDslElement>,
  ).let { ImmutableMap.copyOf(it) }

  override fun getChildPropertiesElementsDescriptionMap(): ImmutableMap<String, PropertiesElementDescription<GradlePropertiesDslElement>> {
    return CHILD_PROPERTIES_ELEMENTS_MAP
  }
}