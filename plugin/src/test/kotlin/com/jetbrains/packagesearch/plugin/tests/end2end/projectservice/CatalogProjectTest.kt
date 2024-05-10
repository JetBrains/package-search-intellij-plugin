package com.jetbrains.packagesearch.plugin.tests.end2end.projectservice

import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogProjectTest : PackageSearchProjectServiceTest() {

    override val resourcePath = "/projects/catalog/"

}