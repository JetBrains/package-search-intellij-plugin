// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageSearch.mppDependencyUpdater.resolved

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
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
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

private val LOG = logger<MppDataNodeProcessor>()

class MppDataNodeProcessor : AbstractProjectDataService<MppCompilationInfoModel, Unit>() {
  object Util {
    val MPP_SOURCES_SETS_MAP_KEY: Key<MppCompilationInfoModel> =
      Key.create(MppCompilationInfoModel::class.java, 100)
  }

  override fun getTargetDataKey(): Key<MppCompilationInfoModel> = Util.MPP_SOURCES_SETS_MAP_KEY

  @Service(Service.Level.PROJECT)
  class Cache(private val project: Project) : Disposable {

    private val cacheFile
      get() = project.getProjectDataPath("pkgs")
        .also { if (!it.exists()) it.createDirectories() }
        .resolve("gradlempp.proto.bin")

    var state = load()
      internal set

    private fun load(): Map<String, MppCompilationInfoModel> =
      cacheFile.takeIf { it.exists() }
        ?.runCatching { ProtoBuf.decodeFromByteArray<Map<String, MppCompilationInfoModel>>(readBytes()) }
        ?.onFailure {
          LOG.debug(this::class.qualifiedName + "#load()", it, "Error while decoding ${cacheFile.absolutePathString()}")
        }
        ?.getOrNull()
        ?.let { emptyMap() }
      ?: emptyMap()

    override fun dispose() {
      cacheFile.writeBytes(ProtoBuf.encodeToByteArray(state))
    }
  }

  override fun importData(
    toImport: Collection<DataNode<MppCompilationInfoModel>>,
    projectData: ProjectData?,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ) {
    project.service<Cache>().state = toImport.associate { it.data.projectDir to it.data }
    super.importData(toImport, projectData, project, modelsProvider)
  }
}