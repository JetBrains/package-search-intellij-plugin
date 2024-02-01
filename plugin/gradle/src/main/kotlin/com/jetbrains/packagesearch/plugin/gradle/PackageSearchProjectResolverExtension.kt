package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModel.Configuration
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModel.Dependency
import com.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleJavaModel
import java.nio.file.Paths
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class PackageSearchProjectResolverExtension : AbstractProjectResolverExtension() {

    override fun getExtraProjectModelClasses() =
        setOf(PackageSearchGradleJavaModel::class.java)

    override fun getToolingExtensionsClasses() =
        setOf(com.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleModelBuilder::class.java)

    private inline fun <reified T> IdeaModule.getExtraProject(): T? =
        resolverCtx.getExtraProject(this@getExtraProject, T::class.java)

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        gradleModule.getExtraProject<PackageSearchGradleJavaModel>()
            ?.toPackageSearchModel()
            ?.also { ideModule.createChild(PackageSearchGradleModel.DATA_NODE_KEY, it) }
        super.populateModuleExtraModels(gradleModule, ideModule)
    }
}

internal fun PackageSearchGradleJavaModel.toPackageSearchModel() =
    PackageSearchGradleModel(
        projectDir = Paths.get(projectDir),
        projectName = projectName,
        projectIdentityPath = projectIdentityPath,
        configurations = configurations.map {
            Configuration(
                name = it.name,
                dependencies = it.dependencies.map { Dependency(it.groupId, it.artifactId, it.version) },
                canBeResolved = it.isCanBeResolved,
                canBeDeclared = it.isCanBeDeclared,
                canBeConsumed = it.isCanBeConsumed
            )
        },
        repositories = repositoryUrls,
        isJavaApplied = isJavaApplied,
        isAmperApplied = isAmperApplied,
        isKotlinAndroidApplied = isKotlinAndroidApplied,
        isKotlinMultiplatformApplied = isKotlinMultiplatformApplied,
        rootProjectName = rootProjectName,
        buildFilePath = buildFilePath?.let { Paths.get(it) },
        rootProjectPath = Paths.get(rootProjectPath)
    )

