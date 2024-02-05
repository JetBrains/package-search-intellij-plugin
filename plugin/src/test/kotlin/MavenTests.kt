import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import com.jetbrains.packagesearch.plugin.tests.PKGS_TEST_DATA_OUTPUT_DIR
import com.jetbrains.packagesearch.plugin.tests.dumps.DumpPackageSearchModules
import kotlin.io.path.PathWalkOption
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.walk
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(JUnit5StarterAssistant::class)
class MavenE2ETests {

    val projectsPath = "/projects/maven/"

    private fun getProjects(): Set<String> {
        return getResourceAbsolutePath(projectsPath)
            ?.walk(PathWalkOption.BREADTH_FIRST)
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: error("Unable to list project files.")
    }

    @Test
    fun `test configuration validation`() = runTest {
        verifyEnvironmentAndFiles(getProjects(), projectsPath)
    }

    /**
     * Cleanup data output dir from project's folders before artifact upload
     */
    @AfterAll
    fun `cleanup data outpdir from project's folders before artifact upload`() {
        PKGS_TEST_DATA_OUTPUT_DIR.walk(PathWalkOption.BREADTH_FIRST)
            .forEach { it.takeIf { it.isDirectory() }?.deleteRecursively() }
    }

    ///split 2 tests 1 for gradle project and 1 for maven (update matrixs and split jobs)
    @ParameterizedTest
    @MethodSource("getProjects")
    fun `verify PKGS Modules`(projectName: String) = runTest(timeout = 30.minutes) {
//        Assumptions.assumeTrue(assertGradleCompatibility())

        //project setup and extraction
        val projectZip = getResourceAbsolutePath("$projectsPath/$projectName.zip")
            ?: error("Project file not found: $projectName.zip")
        projectZip.extractInto(outputDir = PKGS_TEST_DATA_OUTPUT_DIR)

        val projectDir = PKGS_TEST_DATA_OUTPUT_DIR.resolve(projectZip.nameWithoutExtension)

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
        validateResult(projectName, "$projectsPath/$projectName.json")
    }
}