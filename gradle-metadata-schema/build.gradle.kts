plugins {
    alias(packageSearchCatalog.plugins.kotlin.multiplatform)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    alias(packageSearchCatalog.plugins.dokka)
    alias(packageSearchCatalog.plugins.packagesearch.build.config)
    `maven-publish`
}

kotlin {
    jvm()
    js(IR) {
        nodejs()
        browser()
    }
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()
    ios()
    watchos()
    tvos()
    linuxArm64()
    linuxX64()
    macosArm64()
    macosX64()
    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
                api(packageSearchCatalog.kotlinx.serialization.core)
            }
        }
    }
}
