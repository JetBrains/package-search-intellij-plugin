package org.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModel.Configuration
import org.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModel.Dependency
import org.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleModelBuilder
import org.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleJavaModel
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class PackageSearchProjectResolverExtension : AbstractProjectResolverExtension() {

    override fun getExtraProjectModelClasses(): Set<Class<*>> =
        setOf(PackageSearchGradleJavaModel::class.java, Unit::class.java)

    override fun getToolingExtensionsClasses(): Set<Class<*>> =
        setOf(PackageSearchGradleModelBuilder::class.java, Unit::class.java)

    private inline fun <reified T> IdeaModule.getExtraProject(): T? =
        resolverCtx.getExtraProject(this@getExtraProject, T::class.java)

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        gradleModule.getExtraProject<PackageSearchGradleJavaModel>()
            ?.toKotlin()
            ?.also { ideModule.createChild(PackageSearchGradleModelNodeProcessor.ESM_REPORTS_KEY, it) }
        super.populateModuleExtraModels(gradleModule, ideModule)
    }
}

internal fun PackageSearchGradleJavaModel.toKotlin() = PackageSearchGradleModel(
    projectDir = projectDir,
    configurations = configurations.map {
        Configuration(
            it.name,
            it.dependencies.map { Dependency(it.groupId, it.artifactId, it.version) }
        )
    },
    repositories = repositoryUrls,
    isKotlinJsApplied = isKotlinJsApplied,
    isKotlinJvmApplied = isKotlinJvmApplied,
    isKotlinAndroidApplied = isKotlinAndroidApplied,
    isKotlinMultiplatformApplied = isKotlinMultiplatformApplied
)

