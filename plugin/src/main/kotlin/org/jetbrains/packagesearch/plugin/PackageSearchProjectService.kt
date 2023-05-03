@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.application
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.packagesearch.plugin.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.extensions.PackageSearchModuleTransformer
import org.jetbrains.packagesearch.plugin.nitrite.*
import org.jetbrains.packagesearch.plugin.remote.PackageSearchApiClient
import org.jetbrains.packagesearch.plugin.utils.*
import org.jetbrains.packagesearch.plugin.utils.getNativeModulesStateFlow
import kotlin.time.Duration.Companion.days

@Service(Level.PROJECT)
class PackageSearchProjectService(
    private val project: Project,
    val coroutineScope: CoroutineScope,
) {

    companion object {

        private val transformerExtensionName =
            ExtensionPointName<PackageSearchModuleTransformer>("org.jetbrains.packagesearch.moduleTransformer")
    }

    val knownRepositoriesStateFlow =
        interval(1.days) {
            getRepositories(
                repoCache = application.service<PackageSearchCaches>().getRepositoryCache(),
                apiClient = application.service<PackageSearchApiClient>()
            )
        }
            .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), emptyMap())

    val modules = project
        .getNativeModulesStateFlow(coroutineScope)
        .zip(transformerExtensionName.extensionsFlow()) { nativeModules, transformerExtensions ->
            windowedBuilderContext {
                transformerExtensions.flatMap { transformer ->
                    nativeModules.map { module ->
                        transformer.buildModule(module)
                    }
                }
            }
        }
        .flatMapLatest { combine(it) { it.filterNotNull() } }
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), emptyList())

    private suspend fun <T> windowedBuilderContext(
        action: suspend context(PackageSearchModuleBuilderContext) CoroutineScope.() -> T,
    ) = windowedBuilderContext(
        project = project,
        knownRepositories = knownRepositoriesStateFlow.first(),
        packagesCache = application.service<PackageSearchCaches>().getApiPackageCache(),
        action = action,
    )
}
