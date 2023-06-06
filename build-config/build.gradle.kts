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
    implementation(packageSearchCatalog.kotlin.gradlePlugin)
    implementation(packageSearchCatalog.gradle.intellij.platform.plugin)
    implementation(packageSearchCatalog.dokka.gradle.plugin)
}