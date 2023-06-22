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
        artifactId.set("packagesearch-plugin-gradle")
    }
}

intellij {
    plugins.addAll(
        "org.jetbrains.plugins.gradle",
        "org.jetbrains.idea.gradle.dsl"
    )
}

dependencies {
    api(projects.plugin.gradle)
}