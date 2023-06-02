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
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.dizitart.no2.IndexOptions
import org.dizitart.no2.IndexType
import org.jetbrains.packagesearch.plugin.core.nitrite.PackageSearchCaches
import org.jetbrains.packagesearch.plugin.core.nitrite.insert

class PackageSearchGradleModelNodeProcessor :
    AbstractProjectDataService<PackageSearchGradleModel, Unit>() {

    companion object {

        internal val ESM_REPORTS_KEY: Key<PackageSearchGradleModel> =
            Key.create(PackageSearchGradleModel::class.java, 100)

        suspend fun getGradleModelRepository(project: Project) =
            project.service<Cache>().getGradleModelRepository()

    }

    @Service(PROJECT)
    class Cache(private val project: Project, internal val coroutineScope: CoroutineScope) {
        suspend fun getGradleModelRepository() =
            project.service<PackageSearchCaches>()
                .getRepository<GradleModelCacheEntry>("gradle")
                .also {
                    it.createIndex(
                        IndexOptions.indexOptions(IndexType.Unique),
                        GradleModelCacheEntry::data,
                        PackageSearchGradleModel::projectDir
                    )
                }
    }

    override fun getTargetDataKey() = ESM_REPORTS_KEY

    override fun importData(
        toImport: Collection<DataNode<PackageSearchGradleModel>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider,
    ) {
        project.service<Cache>().coroutineScope.launch {
            getGradleModelRepository(project)
                .insert(toImport.map { GradleModelCacheEntry(it.data) })
        }
        super.importData(toImport, projectData, project, modelsProvider)
    }
}


@Serializable
data class GradleModelCacheEntry(
    val data: PackageSearchGradleModel,
    @SerialName("_id") val id: Long? = null,
    val lastUpdate: Instant = Clock.System.now(),
)