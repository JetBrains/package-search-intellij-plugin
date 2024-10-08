@file:Suppress("UnstableApiUsage")

import java.lang.System.getenv

plugins {
    alias(packageSearchCatalog.plugins.shadow) apply false
    alias(packageSearchCatalog.plugins.dokka) apply false
    alias(packageSearchCatalog.plugins.kotlin.jvm) apply false
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization) apply false
    alias(packageSearchCatalog.plugins.kotlin.plugin.compose) apply false
    alias(packageSearchCatalog.plugins.compose.desktop) apply false
    alias(packageSearchCatalog.plugins.kotlinter) apply false
}

allprojects {
    group = "org.jetbrains.packagesearch"
    val baseVersion = "242-SNAPSHOT"

    version = when (val ref = getenv("GITHUB_REF")) {
        null -> baseVersion
        else -> when {
            ref.startsWith("refs/tags/") -> ref.removePrefix("refs/tags/")
            else -> baseVersion
        }
    }
}
