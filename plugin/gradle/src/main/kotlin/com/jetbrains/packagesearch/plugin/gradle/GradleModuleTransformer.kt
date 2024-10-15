@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleProvider
import com.jetbrains.packagesearch.plugin.core.utils.isProjectImportingFlow
import com.jetbrains.packagesearch.plugin.core.utils.smartModeFlow
import com.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleJavaModel
import com.jetbrains.packagesearch.plugin.gradle.utils.GRADLE_MODEL_DATA_NODE_KEY
import com.jetbrains.packagesearch.plugin.gradle.utils.awaitExternalSystemInitialization
import com.jetbrains.packagesearch.plugin.gradle.utils.getModuleChangesFlow
import com.jetbrains.packagesearch.plugin.gradle.utils.initializeProjectFlow
import com.jetbrains.packagesearch.plugin.gradle.utils.isGradle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import org.jetbrains.plugins.gradle.util.GradleUtil.findGradleModuleData
import org.jetbrains.plugins.gradle.util.isResolveTask
import com.intellij.openapi.module.Module as NativeModule

@Deprecated(
    "Use BaseGradleModuleProvider instead",
    ReplaceWith(
        "AbstractGradleModuleProvider",
        "com.jetbrains.packagesearch.plugin.gradle.BaseGradleModuleProvider"
    )
)
typealias BaseGradleModuleProvider = AbstractGradleModuleProvider

abstract class AbstractGradleModuleProvider : PackageSearchModuleProvider {

    override fun provideModule(
        context: PackageSearchModuleBuilderContext,
        nativeModule: NativeModule,
    ): Flow<PackageSearchModule?> =
        merge(context.project.smartModeFlow, context.project.isProjectImportingFlow, context.project.initializeProjectFlow)
            .filter { nativeModule.isGradle }
            .mapNotNull {
                findGradleModuleData(nativeModule)
                    ?.let { ExternalSystemApiUtil.find(it, GRADLE_MODEL_DATA_NODE_KEY) }
                    ?.data
            }
            .flatMapLatest { model ->
                getModuleChangesFlow(model)
                    .map { model }
                    .onStart { emit(model) }
            }
            .transformLatest { model ->
                transform(context, nativeModule, model)
            }

    abstract suspend fun FlowCollector<PackageSearchModule?>.transform(
        context: PackageSearchModuleBuilderContext,
        module: Module,
        model: PackageSearchGradleJavaModel,
    )

    override fun getSyncStateFlow(project: Project) = flow {
        emit(true)
        project.awaitExternalSystemInitialization()
        project.service<GradleSyncNotifierService.State>().collect {
            emit(it)
        }
    }
}

class GradleSyncNotifierService : ExternalSystemTaskNotificationListenerAdapter() {

    @Service(Level.PROJECT)
    class State : MutableStateFlow<Boolean> by MutableStateFlow(false)

    override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        if (isResolveTask(id)) {
            id.findProject()?.service<State>()?.value = true
        }
    }

    override fun onEnd(id: ExternalSystemTaskId) {
        if (isResolveTask(id)) {
            id.findProject()?.service<State>()?.value = false
        }
    }
}
