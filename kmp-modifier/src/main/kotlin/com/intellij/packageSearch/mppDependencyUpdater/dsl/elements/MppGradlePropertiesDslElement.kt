package com.intellij.packageSearch.mppDependencyUpdater.dsl.elements

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement

sealed class MppGradlePropertiesDslElement(
  parent: GradleDslElement?,
  name: GradleNameElement,
) : GradlePropertiesDslElement(parent, null, name),
    SourceSetElement {
  override fun isBlockElement(): Boolean {
    return true
  }
}