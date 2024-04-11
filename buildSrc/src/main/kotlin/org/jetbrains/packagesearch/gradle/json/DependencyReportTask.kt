package org.jetbrains.packagesearch.gradle.json

import kotlin.io.path.writeText
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.configuration
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property


abstract class DependencyReportTask : DefaultTask() {
    @get:InputFiles
    val inputConfigurations = project.objects.listProperty<Configuration>()
        .convention(project.provider { project.configurations.filter { it.isCanBeResolved } })

    @get:OutputDirectory
    val outputDirectory = project.objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir("dependencies-report"))

}

open class JsonDependencyReportTask : DependencyReportTask() {

    @get:Input
    val prettyPrint = project.objects.property<Boolean>()
        .convention(true)

    @get:Input
    val encodeDefaults = project.objects.property<Boolean>()
        .convention(false)

    @get:Input
    val prettyPrintIndent = project.objects.property<String>()
        .convention("  ")

    @Suppress("OPT_IN_USAGE")
    @TaskAction
    fun generate() {

        val context = EncodingContext(
            outputDirectory = outputDirectory.get().asFile.toPath(),
            json = Json {
                prettyPrint = this@JsonDependencyReportTask.prettyPrint.get()
                encodeDefaults = this@JsonDependencyReportTask.encodeDefaults.get()
                prettyPrintIndent = this@JsonDependencyReportTask.prettyPrintIndent.get()
            }
        )

        inputConfigurations.get()
            .asSequence()
            .onEach { logger.lifecycle("Generating report for ${it.name}") }
            .map { configuration -> configuration.toSerializable(context) }
            .forEach { context.outputDirectory.resolve("${it.name}.json").writeText(context.json.encodeToString(it)) }
    }
}