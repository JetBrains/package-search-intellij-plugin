plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
    id("org.jetbrains.compose")
}

intellij {
    version.set("2023.1")
}

dependencies {
    implementation("org.jetbrains.packagesearch:package-search-api-models")
}