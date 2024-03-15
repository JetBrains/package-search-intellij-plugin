package com.jetbrains.packagesearch.plugin.tests.projectservice

import com.jetbrains.packagesearch.plugin.tests.patchGradleVersion
import java.nio.file.Path

class KMPGradleProjectTest : PackageSearchProjectServiceTest() {

    override val resourcePath = "/projects/kmp"
    override fun editProject(projectDir: Path) = patchGradleVersion(
        gradleVersion = System.getenv("GRADLE_VERSION")
            ?: error("GRADLE_VERSION is not set"),
        projectDir = projectDir
    )

}