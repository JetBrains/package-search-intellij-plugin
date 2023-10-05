package com.jetbrains.packagesearch.plugin.core.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.jetbrains.packagesearch.plugin.core.nitrite.buildDefaultNitrate
import com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI
import kotlin.io.path.absolutePathString

@Service(Level.PROJECT)
class PackageSearchProjectCachesService(project: Project ) : Disposable {

    @PKGSInternalAPI
    val cache = buildDefaultNitrate(
        path = project.getProjectDataPath("packagesearch")
            .resolve("cache.db")
            .apply { parent.toFile().mkdirs() }
            .absolutePathString()
    )

    override fun dispose() {
        cache.close()
    }

    inline fun <reified T : Any> getRepository(key: String) =
        cache.getRepository<T>(key)

}

