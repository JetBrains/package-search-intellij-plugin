import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    alias(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.idea.gradle.plugin)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    id("packagesearch")
}

intellij {
    plugins.addAll("org.jetbrains.plugins.gradle")
}

dependencies {
//    implementation(projects.plugin.gradle.tooling)
    implementation(projects.plugin.core)
    compileOnly(packageSearchCatalog.kotlinx.serialization.core)
}