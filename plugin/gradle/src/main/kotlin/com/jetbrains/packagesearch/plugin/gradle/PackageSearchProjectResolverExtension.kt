package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.gradle.tooling.model.idea.IdeaModule
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModel.*
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class PackageSearchProjectResolverExtension : AbstractProjectResolverExtension() {

    override fun getExtraProjectModelClasses() =
        setOf(com.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleJavaModel::class.java)

    override fun getToolingExtensionsClasses() =
        setOf(com.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleModelBuilder::class.java)

    private inline fun <reified T> IdeaModule.getExtraProject(): T? =
        resolverCtx.getExtraProject(this@getExtraProject, T::class.java)

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        gradleModule.getExtraProject<com.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleJavaModel>()
            ?.toKotlin()
            ?.also { ideModule.createChild(PackageSearchGradleModelNodeProcessor.ESM_REPORTS_KEY, it) }
        super.populateModuleExtraModels(gradleModule, ideModule)
    }
}

internal fun com.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleJavaModel.toKotlin() = PackageSearchGradleModel(
    projectDir = projectDir,
    projectName = projectName,
    projectIdentityPath = projectIdentityPath,
    configurations = configurations.map {
        Configuration(
            it.name,
            it.dependencies.map { Dependency(it.groupId, it.artifactId, it.version) }
        )
    },
    repositories = repositoryUrls,
    isKotlinJvmApplied = isKotlinJvmApplied,
    isKotlinAndroidApplied = isKotlinAndroidApplied,
    isKotlinMultiplatformApplied = isKotlinMultiplatformApplied,
    rootProjectName = rootProjectName
)

