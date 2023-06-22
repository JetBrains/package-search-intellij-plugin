plugins {
    id(packageSearchCatalog.plugins.kotlin.jvm)
    id(packageSearchCatalog.plugins.idea.gradle.plugin)
    id(packageSearchCatalog.plugins.dokka)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `build-config`
    `maven-publish`
}

packagesearch {
    publication {
        isEnabled.set(true)
        artifactId.set("packagesearch-plugin-maven")
    }
}

intellij {
    plugins.addAll("org.jetbrains.idea.maven")
}

dependencies {
    api(projects.plugin.core)
    compileOnly(packageSearchCatalog.kotlinx.serialization.core)
}