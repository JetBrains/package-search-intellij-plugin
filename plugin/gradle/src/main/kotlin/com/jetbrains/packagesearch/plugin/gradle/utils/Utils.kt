package com.jetbrains.packagesearch.plugin.gradle.utils

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.plugin.core.utils.isSourceSet
import com.jetbrains.packagesearch.plugin.gradle.GradleDependencyModel
import com.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleJavaModel
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.plugins.gradle.util.GradleConstants

@Suppress("unused")
private val GRADLE_SYSTEM_ID get() = ProjectSystemId("GRADLE")

val GRADLE_MODEL_DATA_NODE_KEY: Key<PackageSearchGradleJavaModel> =
    Key.create(PackageSearchGradleJavaModel::class.java, 100)

val Module.isGradle
    get() = ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, this) && !isSourceSet

suspend fun Project.awaitExternalSystemInitialization() = suspendCancellableCoroutine {
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
            ?: psiElement?.children?.firstOrNull()?.textOffset
            ?: psiElement?.textOffset,
    )

fun ArtifactDependencyModel.toGradleDependencyModel() =
    GradleDependencyModel(
        groupId = group().toString(),
        artifactId = name().toString(),
        version = version().toString() as String?,
        configuration = configurationName(),
        indexes = getDependencyDeclarationIndexes(),
    )


