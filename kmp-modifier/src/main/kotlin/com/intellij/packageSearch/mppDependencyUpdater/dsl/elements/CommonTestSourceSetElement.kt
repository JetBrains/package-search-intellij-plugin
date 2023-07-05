package com.intellij.packageSearch.mppDependencyUpdater.dsl.elements

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.DescribedGradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.google.common.collect.ImmutableMap

class CommonTestSourceSetElement(
  parent: GradleDslElement?,
) : GradleDslBlockElement(parent, nameElement),
    SourceSetElement,
    DescribedGradlePropertiesDslElement<CommonTestSourceSetElement> {
  companion object {
    val COMMON_TEST = PropertiesElementDescription(
      "commonTest",
      CommonTestSourceSetElement::class.java,
    ) { parent: GradleDslElement, _ -> CommonTestSourceSetElement(parent) }

    private val nameElement = GradleNameElement.create("commonTest")
  }

  override fun getDescription(): PropertiesElementDescription<CommonTestSourceSetElement> = COMMON_TEST

  @Suppress("UNCHECKED_CAST")
  private val CHILD_PROPERTIES_ELEMENTS_MAP = mapOf(
    "dependencies" to SourceSetDependenciesElement.DEPENDENCIES as PropertiesElementDescription<GradlePropertiesDslElement>,
  ).let { ImmutableMap.copyOf(it) }

  override fun getChildPropertiesElementsDescriptionMap(): ImmutableMap<String, PropertiesElementDescription<GradlePropertiesDslElement>> {
    return CHILD_PROPERTIES_ELEMENTS_MAP
  }
}