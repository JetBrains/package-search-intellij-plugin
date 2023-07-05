package com.intellij.packageSearch.mppDependencyUpdater.dsl.elements

import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement

sealed interface SourceSetElement : GradleDslElement {
  fun dependencies(): DependenciesDslElement? = getElements()["dependencies"] as? SourceSetDependenciesElement

  fun getElements(): Map<String, GradleDslElement>

  fun setNewElement(newElement: GradleDslElement)
}