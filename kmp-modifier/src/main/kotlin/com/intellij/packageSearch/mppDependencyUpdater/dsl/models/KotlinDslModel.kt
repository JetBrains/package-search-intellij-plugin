// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.dsl.models

import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslDelegatedProperty
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.intellij.packageSearch.mppDependencyUpdater.dsl.elements.*

class KotlinDslModel(private val dslElement: KotlinDslElement) : GradleDslBlockModel(dslElement) {

  val isAvailable: Boolean = dslElement.psiElement != null

  fun sourceSets(): Map<String, SourceSetModel>? {
    val sourceSetsElement = dslElement.elements["sourceSets"] as? SourceSetsElement ?: return null
    return sourceSetsElement.elements
      .mapValues {
        when (it.value) {
          is GradleDslDelegatedProperty -> it.value.children.firstOrNull()
          else -> it.value
        }
      }
      .filterValues { it is SourceSetElement }
      .mapValues { SourceSetModel(it.value as SourceSetElement) }
  }

  fun declareSourceSet(sourceSetName: String): SourceSetModel? {
    val sourceSetsElement = dslElement.elements["sourceSets"] as? SourceSetsElement ?: return null

    val sourceSetElement = when (sourceSetName) {
      // create "$sourceSetName { ... "
      "commonMain" -> CommonMainSourceSetElement(sourceSetsElement).also { sourceSetsElement.setNewElement(it) }
      "commonTest" -> CommonTestSourceSetElement(sourceSetsElement).also { sourceSetsElement.setNewElement(it) }
      else -> {
        // create "val $sourceSetName by getting { ... "
        val sourceSetProperty = GradleDslDelegatedProperty(sourceSetsElement, GradleNameElement.create(sourceSetName))
        sourceSetProperty.externalSyntax = ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT
        sourceSetProperty.elementType = PropertyType.DERIVED

        val getting = GettingDslElement(sourceSetProperty)
        sourceSetProperty.setNewElement(getting)
        sourceSetsElement.setNewElement(sourceSetProperty)

        getting
      }
    }

    return SourceSetModel(sourceSetElement)
  }
}