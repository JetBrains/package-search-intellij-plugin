package com.jetbrains.packagesearch.plugin.core.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.PackageSearch
import com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI
import com.jetbrains.packagesearch.plugin.core.utils.packageSearchProjectDataPath
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import org.dizitart.kno2.getRepository
import org.dizitart.kno2.loadModule
import org.dizitart.kno2.nitrite
import org.dizitart.kno2.serialization.KotlinXSerializationMapper
import org.dizitart.no2.mvstore.MVStoreModule

@Service(Level.PROJECT)
class PackageSearchProjectCachesService(private val project: Project) : Disposable {

    private val cacheFilePath
        get() = project.packageSearchProjectDataPath / "caches-v${PackageSearch.databaseVersion}.db"

    @PKGSInternalAPI
    val cache = nitrite {
        validateRepositories = false
        loadModule(KotlinXSerializationMapper)
        loadModule(
            MVStoreModule.withConfig()
                .filePath(
                    cacheFilePath
                        .createParentDirectories()
                        .absolutePathString()
                )
                .compress(true)
                .build()
        )
    }

    override fun dispose() = cache.close()

    inline fun <reified T : Any> getRepository(key: String) =
        cache.getRepository<T>(key)

}

