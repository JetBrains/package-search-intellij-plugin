// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.dsl.elements

import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription

// This class represents dependencies block inside a source set.
// It is required, because DependenciesDslElement is `insignificant if empty` by default,
// which means that it is deleted if everything is removed from it
// (that is unwanted, because i.e. during the batch update everything is removed and only later is added with updates).
class SourceSetDependenciesElement(
  parent: GradleDslElement,
  name: GradleNameElement,
) : DependenciesDslElement(parent, name) {
  companion object {
    val DEPENDENCIES = PropertiesElementDescription(
      "dependencies",
      SourceSetDependenciesElement::class.java,
    ) { parent: GradleDslElement, name: GradleNameElement -> SourceSetDependenciesElement(parent, name) }
  }

  override fun isInsignificantIfEmpty(): Boolean {
    return false
  }
}