package com.jetbrains.packagesearch.plugin.tests.end2end.projectservice

import com.intellij.ide.starter.runner.TestContainerImpl
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MavenProjectTest : PackageSearchProjectServiceTest() {

    private lateinit var context: TestContainerImpl
    override fun getContext() = context

    override val resourcePath = "/projects/maven/"

}