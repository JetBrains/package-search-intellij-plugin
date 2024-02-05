import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import com.jetbrains.packagesearch.plugin.tests.PKGS_TEST_DATA_OUTPUT_DIR
import com.jetbrains.packagesearch.plugin.tests.dumps.DumpPackageSearchModules
import com.jetbrains.packagesearch.plugin.utils.logWarn
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.copyTo
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readLines
import kotlin.io.path.walk
import kotlin.io.path.writeLines
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(JUnit5StarterAssistant::class)
class GradleE2ETests {

    val projectsPath = "/projects/gradle"

    private fun getProjects() =
        getResourceAbsolutePath("/projects/gradle/")
            ?.walk(PathWalkOption.BREADTH_FIRST)
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: error("Unable to list project files.")

    @Test
    fun `test configuration validation`() {
        runTest {
            verifyEnvironmentAndFiles(projectNames = getProjects(), projectsPath = projectsPath)
        }
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
//        [ '5.6.4', '6.9.2', '7.6.4', '8.6', 'latest' ]
        val gradleVersionToSet = System.getenv("GRADLE_VERSION") ?: "7.6.4"
        patchGradleVersion(gradleVersionToSet, projectDir)

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

/**
 * Patches the gradle version in the gradle wrapper properties file of a project.
 *
 * @param gradleVersion The version of Gradle to patch.
 * @param projectDir The directory of the project.
 */
private fun patchGradleVersion(gradleVersion: String, projectDir: Path) {
    val gradleProperties = projectDir.resolve("gradle/wrapper/gradle-wrapper.properties")
    if (!gradleProperties.isRegularFile()) error("unable to find gradle wrapper properties file")
    logWarn("patching project's gradle version to $gradleVersion")
    gradleProperties.writeLines(
        gradleProperties.readLines()
            .map { line ->
                when {
                    line.startsWith("distributionUrl=") -> {
                        "distributionUrl=https\\://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
                    }

                    else -> {
                        line
                    }
                }
            }
    )
    // copy patched file for manual check
    // NOTE: ci/cd will upload all files (non dir) in PKGS_TEST_DATA_OUTPUT_DIR as artifact
    gradleProperties.copyTo(
        target = PKGS_TEST_DATA_OUTPUT_DIR.resolve("patched-gradle-wrapper.properties"),
        overwrite = true
    )
}
