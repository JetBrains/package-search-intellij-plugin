@file:Suppress("CompanionObjectInExtension")

package org.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.IndexType
import org.jetbrains.packagesearch.plugin.core.nitrite.NitriteFilters
import org.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import org.jetbrains.packagesearch.plugin.core.nitrite.div
import org.jetbrains.packagesearch.plugin.core.utils.PackageSearchProjectCachesService


class PackageSearchGradleModelNodeProcessor :
    AbstractProjectDataService<PackageSearchGradleModel, Unit>() {

    companion object {

        internal val ESM_REPORTS_KEY: Key<PackageSearchGradleModel> =
            Key.create(PackageSearchGradleModel::class.java, 100)

    }

    @Service(PROJECT)
    private class Cache(private val project: Project, private val coroutineScope: CoroutineScope) {
        private val gradleModelRepository = coroutineScope.async {
            project.PackageSearchProjectCachesService
                .getRepository<GradleModelCacheEntry>("gradle")
                .also {
                    it.createIndex(
                        indexOptions = IndexOptions.indexOptions(IndexType.Unique),
                        path = GradleModelCacheEntry::data / PackageSearchGradleModel::projectIdentityPath
                    )
                }
        }
        fun update(items: List<GradleModelCacheEntry>) {
            coroutineScope.launch {
                items.forEach { cacheEntry ->
                    gradleModelRepository.await().update(
                        filter = NitriteFilters.Object.eq(
                            path = GradleModelCacheEntry::data / PackageSearchGradleModel::projectIdentityPath,
                            value = cacheEntry.data.projectIdentityPath
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