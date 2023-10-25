package org.jetbrains.packagesearch.gradle

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property

open class GeneratePackageSearchObject @Inject constructor(objects: ObjectFactory) : DefaultTask() {

    @get:Input
    val pluginId = objects.property<String>()

    @get:Input
    val pluginVersion = objects.property<String>()
        .convention(project.version.toString())

    @get:Input
    val deleteCachesOnStartup = objects.property<Boolean>()
        .convention(System.getenv("CI") != "true")

    @get:Input
    val packageName = objects.property<String>()

    @get:Input
    val objectName = objects.property<String>()
        .convention("PackageSearch")

    @get:OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    @TaskAction
    fun generate() {
        val fileSpec = FileSpec.builder(packageName.get(), objectName.get())
            .addType(
                TypeSpec.objectBuilder(objectName.get())
                    .addModifiers(KModifier.DATA)
                    .addProperty(
                        PropertySpec.builder("pluginId", String::class)
                            .getter(
                                FunSpec.getterBuilder()
                                    .addStatement("return %S", pluginId.get())
                                    .build()
                            )
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("pluginVersion", String::class)
                            .getter(
                                FunSpec.getterBuilder()
                                    .addStatement("return %S", pluginVersion.get())
                                    .build()
                            )
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("deleteCachesOnStartup", Boolean::class)
                            .getter(
                                FunSpec.getterBuilder()
                                    .addStatement("return ${deleteCachesOnStartup.get()}")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
        fileSpec.writeTo(outputDir.get().asFile)
    }
}