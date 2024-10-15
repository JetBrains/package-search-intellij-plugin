package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleJavaModel
import com.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleModelBuilder
import com.jetbrains.packagesearch.plugin.gradle.utils.GRADLE_MODEL_DATA_NODE_KEY
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class PackageSearchProjectResolverExtension : AbstractProjectResolverExtension() {

    override fun getExtraProjectModelClasses() =
        setOf(PackageSearchGradleJavaModel::class.java)

    override fun getToolingExtensionsClasses() =
        setOf(PackageSearchGradleModelBuilder::class.java)

    private inline fun <reified T> IdeaModule.getExtraProject(): T? =
        resolverCtx.getExtraProject(this@getExtraProject, T::class.java)

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        gradleModule.getExtraProject<PackageSearchGradleJavaModel>()
            ?.also { ideModule.createChild(GRADLE_MODEL_DATA_NODE_KEY, it) }
        super.populateModuleExtraModels(gradleModule, ideModule)
    }
}
