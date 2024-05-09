package com.jetbrains.packagesearch.plugin.tests.projectservice

import com.intellij.ide.starter.runner.TestContainerImpl
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MavenProjectTest : PackageSearchProjectServiceTest() {

    private lateinit var testInfo: TestInfo
    private lateinit var context: TestContainerImpl
    override fun getContext(): TestContainerImpl {
        return context
    }

    override val resourcePath = "/projects/maven/"

}