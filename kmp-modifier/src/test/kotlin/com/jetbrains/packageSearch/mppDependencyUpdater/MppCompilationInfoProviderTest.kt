package com.jetbrains.packageSearch.mppDependencyUpdater

import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoProvider
import org.junit.Test
import org.junit.jupiter.api.Assertions

class MppCompilationInfoProviderTest : MppGradleImportingTestBase() {
  @Test
  fun testSimple() {
    doTest {
      val expected = mapOf(
        MppCompilationInfoModel.SourceSet("commonMain") to setOf(
          MppCompilationInfoModel.Jvm,
          MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY),
        ),
        MppCompilationInfoModel.SourceSet("commonTest") to setOf(
          MppCompilationInfoModel.Jvm,
          MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY),
        ),
        MppCompilationInfoModel.SourceSet("jvmMain") to setOf(MppCompilationInfoModel.Jvm),
        MppCompilationInfoModel.SourceSet("jvmTest") to setOf(MppCompilationInfoModel.Jvm),
        MppCompilationInfoModel.SourceSet("jsMain") to setOf(MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY)),
        MppCompilationInfoModel.SourceSet("jsTest") to setOf(MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY)),
      )

      Assertions.assertEquals(expected, MppCompilationInfoProvider.sourceSetsMap(it))
    }
  }

  @Test
  fun testSimpleWithJsIR() {
    doTest {
      val expected = mapOf(
        MppCompilationInfoModel.SourceSet("commonMain") to setOf(
          MppCompilationInfoModel.Jvm,
          MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.IR),
          ),
        MppCompilationInfoModel.SourceSet("commonTest") to setOf(
          MppCompilationInfoModel.Jvm,
          MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.IR),
        ),
        MppCompilationInfoModel.SourceSet("jvmMain") to setOf(MppCompilationInfoModel.Jvm),
        MppCompilationInfoModel.SourceSet("jvmTest") to setOf(MppCompilationInfoModel.Jvm),
        MppCompilationInfoModel.SourceSet("jsMain") to setOf(MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.IR)),
        MppCompilationInfoModel.SourceSet("jsTest") to setOf(MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.IR)),
      )

      Assertions.assertEquals(expected, MppCompilationInfoProvider.sourceSetsMap(it))
    }
  }

  @Test
  fun testShoppingList() {
    doTest {
      val expected = mapOf(
        MppCompilationInfoModel.SourceSet("commonMain") to setOf(
          MppCompilationInfoModel.Jvm,
          MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY),
          MppCompilationInfoModel.Native("ios_arm64"),
          MppCompilationInfoModel.Native("ios_x64"),
          MppCompilationInfoModel.Native("ios_simulator_arm64")
        ),
        MppCompilationInfoModel.SourceSet("commonTest") to setOf(
          MppCompilationInfoModel.Jvm,
          MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY),
          MppCompilationInfoModel.Native("ios_arm64"),
          MppCompilationInfoModel.Native("ios_x64"),
          MppCompilationInfoModel.Native("ios_simulator_arm64")
        ),
        MppCompilationInfoModel.SourceSet("iosMain") to setOf(
          MppCompilationInfoModel.Native("ios_arm64"),
          MppCompilationInfoModel.Native("ios_x64"),
          MppCompilationInfoModel.Native("ios_simulator_arm64")
        ),
        MppCompilationInfoModel.SourceSet("iosTest") to setOf(
          MppCompilationInfoModel.Native("ios_arm64"),
          MppCompilationInfoModel.Native("ios_x64"),
          MppCompilationInfoModel.Native("ios_simulator_arm64")
        ),
        MppCompilationInfoModel.SourceSet("iosArm64Main") to setOf(
          MppCompilationInfoModel.Native("ios_arm64")
        ),
        MppCompilationInfoModel.SourceSet("iosArm64Test") to setOf(
          MppCompilationInfoModel.Native("ios_arm64")
        ),
        MppCompilationInfoModel.SourceSet("iosX64Main") to setOf(
          MppCompilationInfoModel.Native("ios_x64"),
        ),
        MppCompilationInfoModel.SourceSet("iosX64Test") to setOf(
          MppCompilationInfoModel.Native("ios_x64"),
        ),
        MppCompilationInfoModel.SourceSet("iosSimulatorArm64Main") to setOf(
          MppCompilationInfoModel.Native("ios_simulator_arm64")
        ),
        MppCompilationInfoModel.SourceSet("iosSimulatorArm64Test") to setOf(
          MppCompilationInfoModel.Native("ios_simulator_arm64")
        ),
        MppCompilationInfoModel.SourceSet("jsMain") to setOf(
          MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY),
        ),
        MppCompilationInfoModel.SourceSet("jsTest") to setOf(
          MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY),
        ),
        MppCompilationInfoModel.SourceSet("jvmMain") to setOf(
          MppCompilationInfoModel.Jvm
        ),
        MppCompilationInfoModel.SourceSet("jvmTest") to setOf(
          MppCompilationInfoModel.Jvm
        ),
      )

      Assertions.assertEquals(expected, MppCompilationInfoProvider.sourceSetsMap(it))
    }
  }
}