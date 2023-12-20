package com.jetbrains.packagesearch.plugin.ui.model

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.utils.availableExtensionsFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class NoModulesFoundViewMode(private val project: Project) : Disposable {

    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob())

    private val isRefreshingChannel = Channel<Boolean>()

    val isRefreshing = isRefreshingChannel.consumeAsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasExternalProjects = ExternalSystemManager.EP_NAME
        .availableExtensionsFlow
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = ExternalSystemManager.EP_NAME.extensions.isNotEmpty()
        )

    fun refreshExternalProjects() {
        isRefreshingChannel.trySend(true)
        viewModelScope.launch(Dispatchers.Main) {
            ExternalSystemManager.EP_NAME.extensions
                .map {
                    ImportSpecBuilder(project, it.systemId)
                        .callback(handleRefreshCallback())
                }
                .forEach { ExternalSystemUtil.refreshProjects(it) }
        }
    }

    private fun handleRefreshCallback() = object : ExternalProjectRefreshCallback {
        override fun onSuccess(
            externalTaskId: ExternalSystemTaskId,
            externalProject: DataNode<ProjectData>?,
        ) {
            isRefreshingChannel.trySend(false)
        }

        override fun onSuccess(externalProject: DataNode<ProjectData>?) {
            isRefreshingChannel.trySend(false)
        }

        override fun onFailure(
            externalTaskId: ExternalSystemTaskId,
            errorMessage: String,
            errorDetails: String?,
        ) {
            isRefreshingChannel.trySend(false)
        }

        override fun onFailure(errorMessage: String, errorDetails: String?) {
            isRefreshingChannel.trySend(false)
        }
    }

    override fun dispose() {
        viewModelScope.cancel()
    }

}