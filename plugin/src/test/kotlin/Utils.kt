import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.jetbrains.packagesearch.plugin.tests.dumps.DumpPackageSearchModules
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.toPath
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.TestScope

internal fun Any.getResourceAbsolutePath(childPath: String) =
    this::class.java.getResource(childPath)?.toURI()?.toPath()

fun TestScope.verifyEnvironmentAndFiles(projectNames: Set<String>, projectsPath: String) {
    assertNotNull(
        System.getenv("PKGS_PLUGIN_ARTIFACT_FILE"),
        "environment variable PKGS_PLUGIN_ARTIFACT_FILE is not set for the test, " +
                "should be set to the path of the plugin artifact file"
    )
    assertNotNull(
        System.getenv("PKGS_PLUGIN_ID"),
        "environment variable PKGS_PLUGIN_ID is not set for the test, should be set to the plugin id"
    )
    assert(projectNames.isNotEmpty())
    projectNames.forEach { projectName: String ->
        val projectZip = getResourceAbsolutePath("$projectsPath/$projectName.zip")
            ?: error("Project file not found: $projectName.zip")
        val expectedResult = getResourceAbsolutePath("$projectsPath/$projectName.json")
            ?: error("expected result file not found: $projectName.json")
        assert(projectZip.isRegularFile()) {
            "project file not found or is not a file: $projectName.zip"
        }
        assert(expectedResult.isRegularFile()) {
            "expected result file not found or is not a file: $projectName.json"
        }
    }
}

internal fun Path.extractInto(outputDir: Path) {
    ZipFile(toFile()).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            val outputFile = outputDir.resolve(entry.name)
            if (entry.isDirectory) {
                outputFile.createDirectories()
            } else {
                outputFile.parent.createDirectories() // Ensure parent directories exist
                zip.getInputStream(entry).use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

internal fun <T : CommandChain> T.dumpPKGSTestData(): T {
    addCommand(DumpPackageSearchModules.PREFIX)
    return this
}