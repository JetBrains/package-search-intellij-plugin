// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.resolved

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.util.io.createDirectories
import com.intellij.util.io.readBytes
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

private val LOG = logger<MppDataNodeProcessor>()

class MppDataNodeProcessor : AbstractProjectDataService<MppCompilationInfoModel, Unit>() {
    object Util {
        val MPP_SOURCES_SETS_MAP_KEY: Key<MppCompilationInfoModel> =
            Key.create(MppCompilationInfoModel::class.java, 100)
    }

    override fun getTargetDataKey(): Key<MppCompilationInfoModel> = Util.MPP_SOURCES_SETS_MAP_KEY

    @Service(Level.PROJECT)
    class Cache(private val project: Project, coroutineScope: CoroutineScope) {

        private val cacheFile
            get() = project.getProjectDataPath("pkgs")
                .also { if (!it.exists()) it.createDirectories() }
                .resolve("gradlempp.proto.bin")

        val state = MutableStateFlow(load())

        private fun load(): Map<String, MppCompilationInfoModel> {
            if (!cacheFile.exists())
                return emptyMap()
            val bytes = cacheFile.readBytes()
            if (bytes.isEmpty())
                return emptyMap()
            return ProtoBuf.decodeFromByteArray(bytes)
        }

        init {
            state
                .onEach { cacheFile.writeBytes(ProtoBuf.encodeToByteArray(it)) }
                .flowOn(Dispatchers.IO)
                .launchIn(coroutineScope)
        }
    }

    override fun importData(
        toImport: Collection<DataNode<MppCompilationInfoModel>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider,
    ) {
        project.service<Cache>().state.value = toImport.associate { it.data.projectDir to it.data }
        super.importData(toImport, projectData, project, modelsProvider)
    }
}