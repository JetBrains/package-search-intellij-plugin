package com.jetbrains.packagesearch.plugin.core.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.jetbrains.packagesearch.plugin.core.nitrite.buildDefaultNitrate
import com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class PackageSearchProjectCachesService(project: Project, coroutineScope: CoroutineScope) {

    @PKGSInternalAPI
    val cache = coroutineScope.buildDefaultNitrate(
        path = project.getProjectDataPath("packagesearch")
            .resolve("cache.db")
            .apply { parent.toFile().mkdirs() }
            .absolutePathString()
    )

    suspend inline fun <reified T : Any> getRepository(key: String) =
        cache.await().getRepository<T>(key)

}
