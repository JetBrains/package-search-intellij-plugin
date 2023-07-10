package com.jetbrains.packageSearch.mppDependencyUpdater

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.readText
import com.jetbrains.packageSearch.mppDependencyUpdater.intellijStuff.KotlinGradleImportingTestCase
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache
import org.junit.Rule
import org.junit.jupiter.api.Assertions
import org.junit.rules.TestName
import org.junit.runners.Parameterized
import java.io.File

@Suppress("ACCIDENTAL_OVERRIDE")
sealed class MppGradleImportingTestBase : KotlinGradleImportingTestCase() {
  @JvmField
  @Rule
  var name: TestName = TestName()

  internal fun doTest(action: (Module) -> Unit) {
    configureByFiles()
    importProject()

    val module = myProject.modules.first()

    action(module)
  }

  internal fun assertBuildFileIsAsExpected(module: Module) {
    val exProject = findRootExternalProjectOrNull(myProject, module)
                    ?: error("Could not get external project for project $myProject, module $module")
    val buildFile = exProject.buildFile?.absolutePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
                    ?: error("Could not get buildFile for project $myProject, module $module")

    val resultText = buildFile.readText()
    val expectedText = expectedFileContent(name.sanitizedMethodName())

    Assertions.assertEquals(expectedText, resultText)
  }

  override fun testDataDirectory(): File {
    return BASE_DIR.resolve(getTestName(true).substringBefore('_'))
  }

  private fun findRootExternalProjectOrNull(project: Project, module: Module): ExternalProject? {
    val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
    if (rootProjectPath == null) {
      return null
    }

    val externalProjectDataCache = ExternalProjectDataCache.getInstance(project)
    return externalProjectDataCache.getRootExternalProject(rootProjectPath)
  }

  private fun expectedFileContent(testName: String): String {
    val file = testDataDirectory().resolve("_expected").resolve(testName)
    if (!file.exists()) error("Could not file expected file for test $testName")
    return file.readText()
  }

  private fun TestName.sanitizedMethodName(): String = methodName.split("[").first()

  private val BASE_DIR = File("src/test/resources")

  companion object {
    private val SUPPORTED_GRADLE_VERSIONS = arrayOf("8.0")

    @JvmStatic
    @Suppress("ACCIDENTAL_OVERRIDE")
    @Parameterized.Parameters(name = "{index}: with Gradle-{0}")
    fun data(): Collection<Array<Any>> = SUPPORTED_GRADLE_VERSIONS.map { arrayOf(it) }
  }
}