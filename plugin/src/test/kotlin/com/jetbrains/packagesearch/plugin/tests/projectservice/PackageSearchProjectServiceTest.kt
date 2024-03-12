package com.jetbrains.packagesearch.plugin.tests.projectservice

import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import com.jetbrains.packagesearch.plugin.tests.PKGS_TEST_DATA_OUTPUT_DIR
import com.jetbrains.packagesearch.plugin.tests.buildIdeContext
import com.jetbrains.packagesearch.plugin.tests.dumpPKGSTestData
import com.jetbrains.packagesearch.plugin.tests.dumps.DumpPackageSearchModules
import com.jetbrains.packagesearch.plugin.tests.extractInto
import com.jetbrains.packagesearch.plugin.tests.getResourceAbsolutePath
import com.jetbrains.packagesearch.plugin.tests.verifyEnvironmentAndFiles
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.walk
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

abstract class PackageSearchProjectServiceTest : JUnit5StarterAssistant() {

    abstract val resourcePath: String

    private fun getProjects() =
        getResourceAbsolutePath(resourcePath)
            ?.walk(PathWalkOption.BREADTH_FIRST)
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: error("Unable to list project files.")

    @Test
    fun `test configuration validation`() = runTest {
        verifyEnvironmentAndFiles(projectNames = getProjects(), projectsPath = resourcePath)
    }

    /**
     * Cleanup data output dir from project's folders before artifact upload
     */
    @AfterEach
    fun `cleanup data outpdir from project's folders before artifact upload`() {
        PKGS_TEST_DATA_OUTPUT_DIR.listDirectoryEntries()
            .filter { it.isDirectory() }
            .forEach { it.deleteRecursively() }
    }

    open fun editProject(projectDir: Path) {}

    @ParameterizedTest
    @MethodSource("getProjects")
    fun `verify PKGS Modules`(projectName: String) = runTest(timeout = 30.minutes) {
        val projectZip = getResourceAbsolutePath("$resourcePath/$projectName.zip")
            ?: error("Project file not found: $projectName.zip")
        projectZip.extractInto(outputDir = PKGS_TEST_DATA_OUTPUT_DIR)

        val projectDir = PKGS_TEST_DATA_OUTPUT_DIR.resolve(projectZip.nameWithoutExtension)

        editProject(projectDir)

        //IDE context setup
        val testContext = buildIdeContext(projectDir)

        val dumpPkgsDataChain = CommandChain()
            .waitForSmartMode()
            .dumpPKGSTestData()
            .exitApp()

        testContext.runIdeInBackground(
            commands = dumpPkgsDataChain,
            launchName = DumpPackageSearchModules.DUMP_NAME
        ).await()

        //result validation
        validateResult(projectName, "$resourcePath/$projectName.json")
    }
}
