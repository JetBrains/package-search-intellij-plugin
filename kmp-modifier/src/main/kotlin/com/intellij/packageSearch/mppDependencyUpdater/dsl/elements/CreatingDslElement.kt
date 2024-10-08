package com.intellij.packageSearch.mppDependencyUpdater.dsl.elements

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.DescribedGradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.google.common.collect.ImmutableMap

class CreatingDslElement(
  parent: GradleDslElement?,
) : MppGradlePropertiesDslElement(parent, nameElement),
    SourceSetElement,
    DescribedGradlePropertiesDslElement<CreatingDslElement> {
  companion object {
    val CREATING = PropertiesElementDescription(
      "creating",
      CreatingDslElement::class.java,
    ) { parent: GradleDslElement, _ -> CreatingDslElement(parent) }

    private val nameElement = GradleNameElement.create("creating")
  }

  override fun getDescription(): PropertiesElementDescription<CreatingDslElement> = CREATING

  @Suppress("UNCHECKED_CAST")
  private val CHILD_PROPERTIES_ELEMENTS_MAP = mapOf(
    "dependencies" to SourceSetDependenciesElement.DEPENDENCIES as PropertiesElementDescription<GradlePropertiesDslElement>,
  ).let { ImmutableMap.copyOf(it) }

  override fun getChildPropertiesElementsDescriptionMap(kind: GradleDslNameConverter.Kind?) =
    CHILD_PROPERTIES_ELEMENTS_MAP
}