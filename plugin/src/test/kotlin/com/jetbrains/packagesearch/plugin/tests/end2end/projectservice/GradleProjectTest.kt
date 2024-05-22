package com.jetbrains.packagesearch.plugin.tests.end2end.projectservice

import com.intellij.ide.starter.runner.TestContainerImpl
import com.jetbrains.packagesearch.plugin.tests.patchGradleVersion
import java.nio.file.Path
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GradleProjectTest : PackageSearchProjectServiceTest() {

    private lateinit var context: TestContainerImpl
    override fun getContext() = context


    override val resourcePath = "/projects/gradle"
    override fun editProject(projectDir: Path) = patchGradleVersion(
        gradleVersion = System.getenv("GRADLE_VERSION")
            ?: error("GRADLE_VERSION is not set"),
        projectDir = projectDir
    )

}
