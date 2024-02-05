import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import com.jetbrains.packagesearch.plugin.tests.PKGS_TEST_DATA_OUTPUT_DIR
import com.jetbrains.packagesearch.plugin.tests.SerializablePackageSearchModule
import com.jetbrains.packagesearch.plugin.tests.TestResult
import com.jetbrains.packagesearch.plugin.tests.dumps.DumpPackageSearchModules
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readLines
import kotlin.io.path.walk
import kotlin.io.path.writeLines
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(JUnit5StarterAssistant::class)
class E2ETests {


    private fun getProjects() =
        getResourceAbsolutePath("/projects/")
            ?.walk(PathWalkOption.BREADTH_FIRST)
            ?.map { it.nameWithoutExtension }
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: error("Unable to list project files.")

    @ParameterizedTest
    @MethodSource("getProjects")
    fun `verify PKGS Modules`(projectName: String) = runTest(timeout = 10.minutes) {
        val projectZip = getResourceAbsolutePath("/projects/$projectName.zip")
            ?: error("Project file not found: $projectName.zip")

        projectZip.extractInto(outputDir = PKGS_TEST_DATA_OUTPUT_DIR)
        val projectDir = PKGS_TEST_DATA_OUTPUT_DIR.resolve(projectZip.nameWithoutExtension)

        System.getenv("GRADLE_VERSION")?.let {
            println("packing gradle version to $it")
            patchGradleVersion(it, projectDir)
        }

        val testContext = buildIdeContext(projectDir)

        val dumpPkgsDataChain = CommandChain()
            .waitForSmartMode()
            .dumpPKGSTestData()
            .exitApp()

        testContext.runIdeInBackground(
            commands = dumpPkgsDataChain,
            launchName = DumpPackageSearchModules.DUMP_NAME
        ).await()


        val result = Json
            .decodeFromStream<TestResult<Map<String, SerializablePackageSearchModule>>>(
                PKGS_TEST_DATA_OUTPUT_DIR.resolve("modules.json").toFile().inputStream()
            )

        assertNull(
            result.error,
            "Test failed with error: ${result.error?.message}"
        )

        val expected = Json
            .decodeFromStream<TestResult<Map<String, SerializablePackageSearchModule>>>(
                getResourceAbsolutePath("/assertionsData/$projectName.json")
                    ?.toFile()
                    ?.inputStream()
                    ?: error { "assertion data not found for project $projectName" }
            )

        assertNotNull(expected.value)
        assertNotNull(result.value)

        assert(expected.value!!.keys.any { it in result.value!!.keys }) {
            "expected MODULE keys differ from result keys"
        }

        expected.value!!.forEach { (key, value) ->
            validateModule(value, result.value!![key]!!)
        }

    }

    private fun patchGradleVersion(gradleVersion: String, projectDir: Path) {
        val gradleProperties = projectDir.resolve("gradle/wrapper/gradle-wrapper.properties")
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
    }


}


private fun validateModule(
    expected: SerializablePackageSearchModule,
    result: SerializablePackageSearchModule,
) {
    val json by lazy { Json { prettyPrint = true } }
    val printableJson by lazy {
        buildString {
            appendLine("expected:")
            appendLine(json.encodeToString(SerializablePackageSearchModule.serializer(), expected))
            appendLine("result:")
            appendLine(json.encodeToString(SerializablePackageSearchModule.serializer(), result))
        }
    }

    expected.compatiblePackageTypes.let {
        assert(it.size == result.compatiblePackageTypes.size) {
            buildString {
                appendLine("compatible packageType size differ from expected dump")
                appendLine(printableJson)
            }
        }
        assert(it.all { it in result.compatiblePackageTypes }) {
            buildString {
                appendLine("compatible packageType differ from expected dump")
                appendLine(printableJson)
            }
        }
    }

    expected.declaredKnownRepositories.let {
        assert(it.keys.all { it in result.declaredKnownRepositories.keys }) {
            buildString {
                appendLine("declaredKnownRepositories keys differ from expected dump")
                appendLine(printableJson)
            }
        }
        assert(it.values.all { it in result.declaredKnownRepositories.values }) {
            buildString {
                appendLine("declaredKnownRepositories values differ from expected dump")
                appendLine(printableJson)
            }
        }
    }

    assert(expected.identity == result.identity) {
        buildString {
            appendLine("identity differ from expected dump")
            appendLine(printableJson)
        }
    }

}