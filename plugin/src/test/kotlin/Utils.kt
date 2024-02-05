import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.jetbrains.packagesearch.plugin.tests.dumps.DumpPackageSearchModules
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.toPath

internal fun Any.getResourceAbsolutePath(childPath: String) =
    this::class.java.getResource(childPath)?.toURI()?.toPath()

internal fun buildIdeContext(projectPath: Path): IDETestContext {
    val testCase = object : TestCaseTemplate(IdeProductProvider.IC) {
        val project = withProject(LocalProjectInfo(projectPath))
    }
    return Starter.newContext(
        CurrentTestMethod.hyphenateWithClass(),
        testCase.project.useEAP(),
    )
        .prepareProjectCleanImport()
        .setSharedIndexesDownload(true)
        .addProjectToTrustedLocations()
        .also {
            it.pluginConfigurator
                .installPluginFromPath(
                    Path(System.getenv("PKGS_PLUGIN_ARTIFACT_FILE") ?: error("PKGS_PLUGIN_PATH is not set")),
                )
            it.pluginConfigurator.assertPluginIsInstalled(System.getenv("PKGS_PLUGIN_ID"))
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