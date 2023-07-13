@file:Suppress("UnstableApiUsage")
package org.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.packagesearch.plugin.PackageSearchModuleBaseTransformerUtils
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import org.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import org.jetbrains.packagesearch.plugin.core.utils.PackageSearchProjectCachesService
import org.jetbrains.packagesearch.plugin.utils.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@Service(Level.PROJECT)
class PackageSearchProjectService(
    private val project: Project,
    val coroutineScope: CoroutineScope
) {

    val knownRepositoriesStateFlow =
        interval(1.days) {
            getRepositories(
                repoCache = IntelliJApplication.PackageSearchApplicationCachesService.getRepositoryCache(),
                apiClient = IntelliJApplication.PackageSearchApiClientService.client
            )
        }
            .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), emptyMap())

    private val contextFlow= knownRepositoriesStateFlow
            .map {
                WindowedModuleBuilderContext(
                    project = project,
                    knownRepositories = it,
                    packagesCache = IntelliJApplication.PackageSearchApplicationCachesService.getApiPackageCache(),
                    coroutineScope = coroutineScope,
                    projectCaches = project.PackageSearchProjectCachesService.cache.await(),
                    applicationCaches = IntelliJApplication.PackageSearchApplicationCachesService.cache.await(),
                )
            }
            .shareIn(coroutineScope, SharingStarted.WhileSubscribed())

    val modules = combine(
        project.getNativeModulesStateFlow(coroutineScope),
        PackageSearchModuleBaseTransformerUtils.extensionsFlow,
        contextFlow
    ) { nativeModules, transformerExtensions, context ->
        transformerExtensions.flatMap { transformer ->
            nativeModules.map { module ->
                transformer.buildModule(context, module).startWithNull()
            }
        }
    }
        .flatMapLatest { combine(it) { it.filterNotNull() } }
        .filter { it.isNotEmpty() }
        .map { ModulesState.Ready(it) }
        .debounce(1.seconds)
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), ModulesState.Loading)

}

sealed interface ModulesState {
    object Loading : ModulesState
    data class Ready(val modules: List<PackageSearchModuleData>) : ModulesState
}