plugins {
    id(packageSearchCatalog.plugins.kotlin.jvm)
    id(packageSearchCatalog.plugins.idea.gradle.plugin)
    id(packageSearchCatalog.plugins.dokka)
    alias(packageSearchCatalog.plugins.compose.desktop)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `build-config`
    `maven-publish`
}

packagesearch {
    publication {
        isEnabled.set(true)
        artifactId.set("kmp-dependency-modifier")
    }
}

intellij {
    plugins.addAll(
        "org.jetbrains.kotlin",
        "org.jetbrains.plugins.gradle",
        "org.jetbrains.idea.gradle.dsl"
    )
}