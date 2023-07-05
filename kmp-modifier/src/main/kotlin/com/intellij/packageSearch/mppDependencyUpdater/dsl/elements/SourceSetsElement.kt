// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.dsl.elements

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.DescribedGradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.google.common.collect.ImmutableMap

class SourceSetsElement(
  parent: GradleDslElement?,
  name: GradleNameElement,
) : GradleDslBlockElement(parent, name),
    DescribedGradlePropertiesDslElement<SourceSetsElement> {
  companion object {
    val SOURCE_SETS = PropertiesElementDescription(
      "sourceSets",
      SourceSetsElement::class.java,
    ) { parent, name -> SourceSetsElement(parent, name) }
  }

  @Suppress("UNCHECKED_CAST")
  private val CHILD_PROPERTIES_ELEMENTS_MAP = mapOf(
    "getting" to GettingDslElement.GETTING as PropertiesElementDescription<GradlePropertiesDslElement>,
    "creating" to CreatingDslElement.CREATING as PropertiesElementDescription<GradlePropertiesDslElement>,
    "commonMain" to CommonMainSourceSetElement.COMMON_MAIN as PropertiesElementDescription<GradlePropertiesDslElement>,
    "commonTest" to CommonTestSourceSetElement.COMMON_TEST as PropertiesElementDescription<GradlePropertiesDslElement>,
  ).let { ImmutableMap.copyOf(it) }

  override fun getChildPropertiesElementsDescriptionMap(): ImmutableMap<String, PropertiesElementDescription<GradlePropertiesDslElement>> {
    return CHILD_PROPERTIES_ELEMENTS_MAP
  }

  override fun getDescription(): PropertiesElementDescription<SourceSetsElement> = SOURCE_SETS

  override fun isInsignificantIfEmpty(): Boolean {
    return false
  }
}