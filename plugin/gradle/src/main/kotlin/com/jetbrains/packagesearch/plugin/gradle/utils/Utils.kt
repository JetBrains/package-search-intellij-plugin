package com.jetbrains.packagesearch.plugin.gradle.utils

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.plugin.gradle.GradleDependencyModel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPathOrNull

val Module.isGradleSourceSet: Boolean
    get() {
        if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, this)) return false
        return ExternalSystemApiUtil.getExternalModuleType(this) == GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
    }

val Module.gradleIdentityPathOrNull: String?
    get() = CachedModuleDataFinder.getInstance(project)
        .findMainModuleData(this)
        ?.data
        ?.gradleIdentityPathOrNull

suspend fun Project.awaitExternalSystemInitialization() = suspendCoroutine {
    ExternalProjectsManager.getInstance(this@awaitExternalSystemInitialization)
        .runWhenInitialized { it.resume(Unit) }
}

private fun ArtifactDependencyModel.getDependencyDeclarationIndexes(): DependencyDeclarationIndexes =
    DependencyDeclarationIndexes(
        declarationStartIndex = psiElement
            ?.parents
            ?.take(5)
            ?.firstOrNull { configurationName() in it.text }
            ?.textOffset
            ?: psiElement!!.textOffset,
        versionStartIndex = version().psiElement?.textOffset
            ?: psiElement?.children?.firstOrNull()?.textOffset,
    )

fun ArtifactDependencyModel.toGradleDependencyModel() =
    GradleDependencyModel(
        groupId = group().toString(),
        artifactId = name().toString(),
        version = version().toString() as String?,
        configuration = configurationName(),
        indexes = getDependencyDeclarationIndexes(),
    )


