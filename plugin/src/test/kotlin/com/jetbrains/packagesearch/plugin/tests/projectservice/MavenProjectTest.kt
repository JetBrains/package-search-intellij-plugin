package com.jetbrains.packagesearch.plugin.tests.projectservice

import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MavenProjectTest : PackageSearchProjectServiceTest() {

    override val resourcePath = "/projects/maven/"

}