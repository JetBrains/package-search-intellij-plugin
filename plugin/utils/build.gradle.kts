@file:Suppress("UnstableApiUsage")


plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.dokka)
    id(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `maven-publish`
}

tasks {
    test {
        val cacheDir = layout.buildDirectory.dir("tests/cache")
        environment("CACHES", cacheDir.map { it.asFile.absolutePath }.get())
        doFirst {
            val cacheDirectory = cacheDir.get().asFile
            cacheDirectory.deleteRecursively()
            cacheDirectory.mkdirs()
        }
    }
}
