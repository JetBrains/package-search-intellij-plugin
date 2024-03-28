package com.jetbrains.packagesearch.plugin.tests

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.SdkObject
import com.jetbrains.packagesearch.plugin.tests.dumps.DumpPackageSearchModules
import com.jetbrains.packagesearch.plugin.utils.logWarn
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.readLines
import kotlin.io.path.toPath
import kotlin.io.path.writeLines
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

/**
 * Patches the gradle version in the gradle wrapper properties file of a project.
 *
 * @param gradleVersion The version of Gradle to patch.
 * @param projectDir The directory of the project.
 */
internal fun patchGradleVersion(gradleVersion: String, projectDir: Path) {
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

data class JavaLocation(val home: Path, val majorVersion: String)

internal fun fetchJavaLocation(): JavaLocation {
    val javaVersion: String? = System.getenv("JAVA_VERSION")
    val javaHome: String? = System.getenv("JAVA_HOME")

    return when {
        // JAVA_VERSION and JAVA_HOME are set
        javaVersion != null && javaHome != null -> JavaLocation(
            home = javaHome.toNioPathOrNull() ?: error("JAVA_HOME is not a valid path"),
            majorVersion = javaVersion
        )
        // JAVA_VERSION is set
        javaHome != null -> fetchJavaLocation(javaHome.toNioPathOrNull() ?: error("JAVA_HOME is not a valid path"))

        // JAVA_HOME is set, fallback to the JDK running this process
        else -> fetchJavaLocation(
            System.getProperty("java.home")?.toNioPathOrNull() ?: error("java.home is not set?????")
        )
    }
}

private fun fetchJavaLocation(javaHome: Path): JavaLocation {
    val javaVersionFile = javaHome.resolve("release")
        ?: error("JAVA_HOME is not a valid path")
    val javaVersionLine = javaVersionFile.readLines()
        .firstOrNull { it.startsWith("JAVA_VERSION") }
        ?: error("JAVA_VERSION is not found in $javaVersionFile")
    val version = javaVersionLine.split("=")
        .getOrNull(1)
        ?.removePrefix("\"")
        ?.removeSuffix("\"")
        ?.substringBefore(".")
        ?: error("JAVA_VERSION is not found in $javaVersionFile")
    return JavaLocation(javaHome, version)
}


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

    val sdk = fetchJavaLocation().toSdkObject()

    return Starter.newContext(
        CurrentTestMethod.hyphenateWithClass(),
        testCase.project.useRelease("233.3.6"),
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

private fun JavaLocation.toSdkObject() = SdkObject(
    sdkName = "temurin-$majorVersion",
    sdkType = "JavaSDK",
    sdkPath = home
)
