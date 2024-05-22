package com.jetbrains.packagesearch.plugin.tests.end2end.projectservice

import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MavenProjectsTest : PackageSearchProjectServiceTest() {

    override val resourcePath = "/projects/maven/"

}