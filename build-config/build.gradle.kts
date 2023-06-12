plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        create("packagesearch") {
            id = "packagesearch"
            implementationClass = "org.jetbrains.packagesearch.gradle.PackageSearchPlugin"
        }
    }
}

dependencies {
    implementation(packageSearchCatalog.kotlin.gradle.plugin)
    implementation(packageSearchCatalog.gradle.intellij.platform.plugin)
    implementation(packageSearchCatalog.dokka.gradle.plugin)
    implementation(packageSearchCatalog.foojay.resolver.gradle.plugin)
}