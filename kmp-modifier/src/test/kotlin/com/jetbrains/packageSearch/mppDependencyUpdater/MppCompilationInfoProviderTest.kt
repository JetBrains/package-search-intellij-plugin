package com.jetbrains.packageSearch.mppDependencyUpdater

import com.intellij.openapi.components.service
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppDataNodeProcessor
import org.junit.Test
import org.junit.jupiter.api.Assertions

class MppCompilationInfoProviderTest : MppGradleImportingTestBase() {
    @Test
    fun testSimple() {
        doTest {
            val expected = mapOf(
                "commonMain" to setOf(
                    MppCompilationInfoModel.Jvm,
                    MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY),
                ),
                "commonTest" to setOf(
                    MppCompilationInfoModel.Jvm,
                    MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY),
                ),
                "jvmMain" to setOf(MppCompilationInfoModel.Jvm),
                "jvmTest" to setOf(MppCompilationInfoModel.Jvm),
                "jsMain" to setOf(MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY)),
                "jsTest" to setOf(MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY)),
            )

            Assertions.assertEquals(expected, it.project.service<MppDataNodeProcessor.Cache>().state.value)
        }
    }

    @Test
    fun testSimpleWithJsIR() {
        doTest {
            val expected = mapOf(
                "commonMain" to setOf(
                    MppCompilationInfoModel.Jvm,
                    MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.IR),
                ),
                "commonTest" to setOf(
                    MppCompilationInfoModel.Jvm,
                    MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.IR),
                ),
                "jvmMain" to setOf(MppCompilationInfoModel.Jvm),
                "jvmTest" to setOf(MppCompilationInfoModel.Jvm),
                "jsMain" to setOf(MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.IR)),
                "jsTest" to setOf(MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.IR)),
            )

            Assertions.assertEquals(expected, it.project.service<MppDataNodeProcessor.Cache>().state.value)
        }
    }

    @Test
    fun testShoppingList() {
        doTest {
            val expected = mapOf(
                "commonMain" to setOf(
                    MppCompilationInfoModel.Jvm,
                    MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY),
                    MppCompilationInfoModel.Native("ios_arm64"),
                    MppCompilationInfoModel.Native("ios_x64"),
                    MppCompilationInfoModel.Native("ios_simulator_arm64")
                ),
                "commonTest" to setOf(
                    MppCompilationInfoModel.Jvm,
                    MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY),
                    MppCompilationInfoModel.Native("ios_arm64"),
                    MppCompilationInfoModel.Native("ios_x64"),
                    MppCompilationInfoModel.Native("ios_simulator_arm64")
                ),
                "iosMain" to setOf(
                    MppCompilationInfoModel.Native("ios_arm64"),
                    MppCompilationInfoModel.Native("ios_x64"),
                    MppCompilationInfoModel.Native("ios_simulator_arm64")
                ),
                "iosTest" to setOf(
                    MppCompilationInfoModel.Native("ios_arm64"),
                    MppCompilationInfoModel.Native("ios_x64"),
                    MppCompilationInfoModel.Native("ios_simulator_arm64")
                ),
                "iosArm64Main" to setOf(
                    MppCompilationInfoModel.Native("ios_arm64")
                ),
                "iosArm64Test" to setOf(
                    MppCompilationInfoModel.Native("ios_arm64")
                ),
                "iosX64Main" to setOf(
                    MppCompilationInfoModel.Native("ios_x64"),
                ),
                "iosX64Test" to setOf(
                    MppCompilationInfoModel.Native("ios_x64"),
                ),
                "iosSimulatorArm64Main" to setOf(
                    MppCompilationInfoModel.Native("ios_simulator_arm64")
                ),
                "iosSimulatorArm64Test" to setOf(
                    MppCompilationInfoModel.Native("ios_simulator_arm64")
                ),
                "jsMain" to setOf(
                    MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY),
                ),
                "jsTest" to setOf(
                    MppCompilationInfoModel.Js(MppCompilationInfoModel.Js.Compiler.LEGACY),
                ),
                "jvmMain" to setOf(
                    MppCompilationInfoModel.Jvm
                ),
                "jvmTest" to setOf(
                    MppCompilationInfoModel.Jvm
                ),
            )

            Assertions.assertEquals(expected, it.project.service<MppDataNodeProcessor.Cache>().state.value)
        }
    }
}