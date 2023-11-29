package com.jetbrains.packagesearch.plugin.core.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.jetbrains.packagesearch.plugin.core.PackageSearch
import com.jetbrains.packagesearch.plugin.core.nitrite.buildDefaultNitrate
import com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI
import kotlin.io.path.absolutePathString
import kotlin.io.path.div

@Service(Level.PROJECT)
class PackageSearchProjectCachesService(private val project: Project) : Disposable {

    private val cacheFilePath
        get() = cachesDirectory / "db-${PackageSearch.pluginVersion}.db"

    private val cachesDirectory
        get() = project.getProjectDataPath("caches") / "packagesearch"

    @PKGSInternalAPI
    val cache = buildDefaultNitrate(
        path = cacheFilePath
            .apply { parent.toFile().mkdirs() }
            .absolutePathString()
    )

    override fun dispose() {
        cache.close()
    }

    inline fun <reified T : Any> getRepository(key: String) =
        cache.getRepository<T>(key)

}
