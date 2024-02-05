import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.tools.ide.performanceTesting.commands.SdkObject
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Builds the IDE context for testing.
 * Set up the SKD as env var specifies
 * env var JAVA_VERSION, JAVA_HOME
 *
 * @param projectPath The path to the project.
 * @return The IDETestContext object.
 */
internal fun buildIdeContext(projectPath: Path): IDETestContext {
    val testCase = object : TestCaseTemplate(IdeProductProvider.IC) {
        val project = withProject(LocalProjectInfo(projectPath))
    }
    val javaVersion = System.getenv("JAVA_VERSION") ?: error("JAVA_VERSION is not set")
    val javaHome = System.getenv("JAVA_HOME") ?: error("JAVA_HOME is not set")

    val sdk = buildJavaSdkObject(javaHome, javaVersion)

    return Starter.newContext(
        CurrentTestMethod.hyphenateWithClass(),
        testCase.project.useEAP(),
    )
        .setSharedIndexesDownload(true)
        .addProjectToTrustedLocations()
        .disableFusSendingOnIdeClose()
        .setupSdk(sdk) //  <component name="ProjectRootManager" version="2" languageLevel="JDK_1_8" project-jdk-name="..." project-jdk-type="JavaSDK" />
        .also {
            it.pluginConfigurator
                .installPluginFromPath(
                    Path(System.getenv("PKGS_PLUGIN_ARTIFACT_FILE") ?: error("PKGS_PLUGIN_PATH is not set")),
                )
            it.pluginConfigurator.assertPluginIsInstalled(System.getenv("PKGS_PLUGIN_ID"))
        }
}

private fun buildJavaSdkObject(javaHome: String, javaVersion: String) = javaHome.toNioPathOrNull()?.let {
    SdkObject(
        "temurin-$javaVersion",
        "JavaSDK",
        it
    )
} ?: error("Could not determine SDK properties")