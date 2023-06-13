import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.idea.gradle.plugin)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    alias(packageSearchCatalog.plugins.packagesearch.build.config)
}

intellij {
    plugins.addAll("org.jetbrains.idea.maven")
}

dependencies {
    api(projects.plugin.core)
    compileOnly(packageSearchCatalog.kotlinx.serialization.core)
}