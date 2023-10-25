@file:Suppress("CompanionObjectInExtension")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.nitrite.NitriteFilters
import com.jetbrains.packagesearch.plugin.core.nitrite.div
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchProjectCachesService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.IndexType


class PackageSearchGradleModelNodeProcessor :
    AbstractProjectDataService<PackageSearchGradleModel, Unit>() {

    companion object {

        internal val ESM_REPORTS_KEY: Key<PackageSearchGradleModel> =
            Key.create(PackageSearchGradleModel::class.java, 100)

    }

    @Service(PROJECT)
    class Cache(private val project: Project, private val coroutineScope: CoroutineScope) {

        private val gradleModelRepository = project.PackageSearchProjectCachesService
            .getRepository<GradleModelCacheEntry>("gradle")

        init {
            coroutineScope.launch {
                gradleModelRepository.createIndex(
                    indexOptions = IndexOptions.indexOptions(IndexType.Unique),
                    path = GradleModelCacheEntry::data / PackageSearchGradleModel::projectIdentityPath
                )
            }
        }

        suspend fun clean() {
            gradleModelRepository.removeAll()
            ExternalSystemManager.EP_NAME.extensionList.forEach {
                val importSpec = ImportSpecBuilder(project, it.systemId).build()
                withContext(Dispatchers.EDT) {
                    ExternalSystemUtil.refreshProjects(importSpec)
                }
            }
        }

        fun update(items: List<GradleModelCacheEntry>) {
            coroutineScope.launch {
                items.forEach { cacheEntry ->
                    gradleModelRepository.update(
                        filter = NitriteFilters.Object.eq(
                            value = cacheEntry.data.projectIdentityPath,
                            path = GradleModelCacheEntry::data / PackageSearchGradleModel::projectIdentityPath
                        ),
                        update = cacheEntry,
                        upsert = true
                    )
                }
            }
        }
    }

    override fun getTargetDataKey() = ESM_REPORTS_KEY

    override fun importData(
        toImport: Collection<DataNode<PackageSearchGradleModel>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider,
    ) {
        project.service<Cache>().update(toImport.map { it.data.toCacheEntry() })
        super.importData(toImport, projectData, project, modelsProvider)
    }
}

private fun PackageSearchGradleModel.toCacheEntry() =
    GradleModelCacheEntry(this)

@Serializable
data class GradleModelCacheEntry(
    val data: PackageSearchGradleModel,
    @SerialName("_id") val id: Long? = null,
    val lastUpdate: Instant = Clock.System.now(),
)