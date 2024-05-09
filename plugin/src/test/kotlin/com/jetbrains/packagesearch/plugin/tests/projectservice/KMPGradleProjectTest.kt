package com.jetbrains.packagesearch.plugin.tests.projectservice

import com.intellij.ide.starter.runner.TestContainerImpl
import com.jetbrains.packagesearch.plugin.tests.patchGradleVersion
import java.nio.file.Path
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KMPGradleProjectTest : PackageSearchProjectServiceTest() {

    private lateinit var testInfo: TestInfo
    private lateinit var context: TestContainerImpl
    override fun getContext(): TestContainerImpl {
        return context
    }

    override val resourcePath = "/projects/kmp"
    override fun editProject(projectDir: Path) = patchGradleVersion(
        gradleVersion = System.getenv("GRADLE_VERSION")
            ?: error("GRADLE_VERSION is not set"),
        projectDir = projectDir
    )

}