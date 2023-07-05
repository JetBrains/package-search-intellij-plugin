// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.dsl.models

import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.util.PsiElementHolder
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModelImpl
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.intellij.psi.PsiElement
import com.intellij.packageSearch.mppDependencyUpdater.dsl.elements.SourceSetDependenciesElement
import com.intellij.packageSearch.mppDependencyUpdater.dsl.elements.SourceSetElement

class SourceSetModel(
  private val dslElement: SourceSetElement,
) : PsiElementHolder {
  val name: String by lazy {
    dslElement.name
  }

  override fun getPsiElement(): PsiElement? = dslElement.psiElement

  fun dependencies(): DependenciesModel? = dslElement.dependencies()?.let { DependenciesModelImpl(it) }

  fun addDependenciesBlock(): DependenciesModel {
    val depsElement = SourceSetDependenciesElement(dslElement, GradleNameElement.create("dependencies"))
    dslElement.setNewElement(depsElement)
    return DependenciesModelImpl(depsElement)
  }
}