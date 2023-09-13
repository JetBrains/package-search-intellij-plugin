package com.jetbrains.packagesearch.plugin.gradle.utils

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.nitrite.NitriteFilters
import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import com.jetbrains.packagesearch.plugin.core.nitrite.div
import com.jetbrains.packagesearch.plugin.core.utils.flow
import com.jetbrains.packagesearch.plugin.gradle.GradleDependencyModel
import com.jetbrains.packagesearch.plugin.gradle.GradleModelCacheEntry
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleDeclaredPackage
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.singleOrNull
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

suspend fun PackageSearchModuleBuilderContext.getGradleModelRepository(): CoroutineObjectRepository<GradleModelCacheEntry> =
    projectCaches.getRepository<GradleModelCacheEntry>("gradle")

val Project.gradleSyncNotifierFlow
    get() = messageBus.flow(ProjectDataImportListener.TOPIC) {
        object : ProjectDataImportListener {
            override fun onImportFinished(projectPath: String?) {
                trySend(Unit)
            }
        }
    }

val Project.smartModeFlow: Flow<Boolean>
    get() = messageBus.flow(DumbService.DUMB_MODE) {
        object : DumbService.DumbModeListener {
            override fun enteredDumbMode() {
                trySend(false)
            }

            override fun exitDumbMode() {
                trySend(true)
            }
        }
    }
        .onStart { emit(!DumbService.isDumb(this@smartModeFlow)) }

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

internal fun NitriteFilters.Object.gradleModel(identityPath: String) = eq(
    path = GradleModelCacheEntry::data / PackageSearchGradleModel::projectIdentityPath,
    value = identityPath,
)

internal suspend fun CoroutineObjectRepository<GradleModelCacheEntry>.gradleModel(forIdentityPath: String) =
    find(NitriteFilters.Object.gradleModel(forIdentityPath)).singleOrNull()?.data

