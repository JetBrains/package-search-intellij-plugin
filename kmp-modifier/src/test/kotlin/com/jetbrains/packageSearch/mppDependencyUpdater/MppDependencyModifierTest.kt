// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.packageSearch.mppDependencyUpdater

import com.intellij.packageSearch.mppDependencyUpdater.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.jupiter.api.Disabled

class MppDependencyModifierTest : MppGradleImportingTestBase() {
  @Test
  fun testSimple_add() {
    doTest {
      val descriptors = listOf(
        MppModifierAddData(
          "commonMain",
          MppDependency.Maven(
            "1", "2", "3",
            "implementation"
          ),
        ),
        MppModifierAddData(
          "commonTest",
          MppDependency.Maven(
            "4", "2", "3",
            "implementation"
          ),
        )
      )

      runBlocking {
        MppDependencyModifier.addDependencies(it, descriptors)
      }

      assertBuildFileIsAsExpected(it)
    }
  }

  @Test
  fun testSimple_remove() {
    doTest {
      val descriptors = listOf(
        MppModifierRemoveData(
          "commonMain",
          MppDependency.Maven(
            "org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.3.3",
            "implementation"
          ),
        ),
        MppModifierRemoveData(
          "commonMain",
          MppDependency.Maven(
            "io.ktor", "ktor-client-core", "2.0.3",
            "implementation"
          ),
        )
      )

      runBlocking {
        MppDependencyModifier.removeDependencies(it, descriptors)
      }

      assertBuildFileIsAsExpected(it)
    }
  }

  @Test
  fun testSimple_update() {
    doTest {
      val updates = listOf(
        MppModifierUpdateData(
          "commonMain",
          MppDependency.Maven(
            "org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.3.3",
            "implementation"
          ),
          MppDependency.Maven(
            "org.jetbrains.kotlinx", "kotlinx-serialization-json", "2",
            "compile"
          ),
        ),
        MppModifierUpdateData(
          "commonMain",
          MppDependency.Maven(
            "io.ktor", "ktor-client-core", "2.0.3",
            "implementation"
          ),
          MppDependency.Maven(
            "io.ktor", "ktor-client-coreeee", "2.0.3",
            "compile"
          ),
        )
      )

      runBlocking {
        MppDependencyModifier.updateDependencies(it, updates)
      }

      assertBuildFileIsAsExpected(it)
    }
  }

  @Test
  fun testNoDepsBlock_addWithMissingDependenciesBlock() {
    doTest {
      runBlocking {
        MppDependencyModifier.addDependency(
            it,
          "commonMain",
            MppDependency.Maven(
              "1", "2", "3",
              "implementation"
            )
          )
      }

      assertBuildFileIsAsExpected(it)
    }
  }

  @Test
  fun testNoCommonMain_addWithMissingCommonMain() {
    doTest {
      runBlocking {
        MppDependencyModifier.addDependency(
          it,
        "commonMain",
          MppDependency.Maven(
            "1", "2", "3",
            "implementation"
          ),
        )
      }

      assertBuildFileIsAsExpected(it)
    }
  }

  @Test
  fun testNoCommonMain_addWithMissingCustomSourceSet() {
    doTest {
      runBlocking {
        MppDependencyModifier.addDependency(
          it,
          "THIS_DID_NOT_EXIST",
          MppDependency.Maven(
            "1", "2", "3",
            "implementation"
          ),
        )
      }

      assertBuildFileIsAsExpected(it)
    }
  }

  @Test
  @Disabled("For now we do not allow updates with different source sets")
  fun testSimple_updateWithMissingSourceSet() {
    doTest {
      //runBlocking {
      //  MppDependencyModificator.updateDependency(
      //    it,
      //    MppDependency(
      //      UnifiedDependency(
      //        UnifiedCoordinates("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.3.3"),
      //        "implementation"
      //      ),
      //      "commonMain"
      //    ),
      //    MppDependency(
      //      UnifiedDependency(
      //        UnifiedCoordinates("org.jetbrains.kotlinx", "kotlinx-serialization-json", "2"),
      //        "compile"
      //      ),
      //      "THIS_DID_NOT_EXIST"
      //    ),
      //  )
      //}
      //
      //assertBuildFileIsAsExpected(it)
    }
  }

  @Test
  fun testCurlyBracesCommonMain_add() {
    doTest {
      runBlocking {
        MppDependencyModifier.addDependency(
          it,
          "commonMain",
          MppDependency.Maven(
            "1", "2", "3",
            "implementation"
          ),
        )
      }

      assertBuildFileIsAsExpected(it)
    }
  }

  @Test
  fun testByCreating_add() {
    doTest {
      runBlocking {
        MppDependencyModifier.addDependency(
          it,
          "THIS_DID_NOT_EXIST",
          MppDependency.Maven(
            "1", "2", "3",
            "implementation"
          ),
        )
      }

      assertBuildFileIsAsExpected(it)
    }
  }

  @Test
  fun testShoppingList() {
    doTest {
      val descriptor1 = MppDependency.Maven(
        "1", "2", "3",
        "implementation"
      )
      val sourceSet1 = "commonMain"

      val descriptor2 = MppDependency.Maven(
        "4", "2", "3",
        "implementation"
      )

      val descriptor3 = MppDependency.Maven(
        "org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.3.3",
        "implementation"
      )
      val sourceSet3 = "commonMain"

      runBlocking {
        MppDependencyModifier.addDependency(it, sourceSet1, descriptor1)
        MppDependencyModifier.updateDependency(it, sourceSet1, descriptor1, descriptor2)
        MppDependencyModifier.addDependency(it, sourceSet1, descriptor1)
        MppDependencyModifier.removeDependency(it, sourceSet3, descriptor3)
      }

      assertBuildFileIsAsExpected(it)
    }
  }
}