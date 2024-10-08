// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.dsl.elements

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.DescribedGradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.google.common.collect.ImmutableMap

class GettingDslElement(
  parent: GradleDslElement,
) : MppGradlePropertiesDslElement(parent, nameElement),
    SourceSetElement,
    DescribedGradlePropertiesDslElement<GettingDslElement> {
  companion object {
    val GETTING = PropertiesElementDescription(
      "getting",
      GettingDslElement::class.java,
    ) { parent: GradleDslElement, _ -> GettingDslElement(parent) }

    private val nameElement = GradleNameElement.create("getting")
  }

  override fun getDescription(): PropertiesElementDescription<GettingDslElement> = GETTING

  @Suppress("UNCHECKED_CAST")
  private val CHILD_PROPERTIES_ELEMENTS_MAP = mapOf(
    "dependencies" to SourceSetDependenciesElement.DEPENDENCIES as PropertiesElementDescription<GradlePropertiesDslElement>,
  ).let { ImmutableMap.copyOf(it) }

  override fun getChildPropertiesElementsDescriptionMap(kind: GradleDslNameConverter.Kind?) =
    CHILD_PROPERTIES_ELEMENTS_MAP
}