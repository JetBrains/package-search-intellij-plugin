@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.utils.*
import kotlin.time.Duration.Companion.days
import org.jetbrains.packagesearch.plugin.utils.windowedBuilderContext

@Service(Level.PROJECT)
class PackageSearchProjectService(
    private val  project: Project,
    val coroutineScope: CoroutineScope,
) {

    val knownRepositoriesStateFlow =
        interval(1.days) {
            getRepositories(
                repoCache = IntelliJApplication.PackageSearchCachesService.getRepositoryCache(),
                apiClient = IntelliJApplication.PackageSearchApiClientService.client
            )
        }
            .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), emptyMap())

    val modules = project
        .getNativeModulesStateFlow(coroutineScope)
        .zip(PackageSearchModuleBaseTransformerUtils.extensionsFlow) { nativeModules, transformerExtensions ->
            windowedBuilderContext { context ->
                transformerExtensions.flatMap { transformer ->
                    nativeModules.map { module ->
                        transformer.buildModule(context, module)
                    }
                }
            }
        }
        .flatMapLatest { combine(it) { it.filterNotNull() } }
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), emptyList())

    private suspend fun <T> windowedBuilderContext(
        action: suspend CoroutineScope.(context: PackageSearchModuleBuilderContext) -> T,
    ): T = windowedBuilderContext(
        project = project,
        knownRepositories = knownRepositoriesStateFlow.value,
        packagesCache = IntelliJApplication.PackageSearchCachesService.getApiPackageCache(),
        action = action
    )
}

