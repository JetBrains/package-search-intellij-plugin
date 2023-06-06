plugins {
    java
    kotlin("jvm") apply false
    id("org.jetbrains.intellij")
    id("packagesearch")
}

packagesearch {
    javaVersion.set(JavaVersion.VERSION_1_8)
}

intellij {
    plugins.addAll("org.jetbrains.plugins.gradle")
}