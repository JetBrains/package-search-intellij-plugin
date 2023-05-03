plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
    kotlin("plugin.serialization")
}

intellij {
    version.set("2023.1")
    plugins.add("org.jetbrains.idea.maven")
}

kotlin {
    target {
        compilations.all {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xcontext-receivers")
            }
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
}

dependencies {
    implementation(projects.plugin)
}