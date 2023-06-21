@file:Suppress("UnstableApiUsage")
package org.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersionWeakCache
import org.jetbrains.packagesearch.plugin.PackageSearchModuleBaseTransformerUtils
import org.jetbrains.packagesearch.plugin.applyOnEach
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import org.jetbrains.packagesearch.plugin.utils.*
import kotlin.time.Duration.Companion.days

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

    val jsonFLow = PackageSearchModuleBaseTransformerUtils.extensionsFlow
        .map { transformers ->
            Json {
                prettyPrint = true
                serializersModule = SerializersModule {
                    contextual(NormalizedVersionWeakCache)
                    polymorphic<PackageSearchModule> {
                        transformers.applyOnEach { registerModuleSerializer() }
                    }
                    polymorphic<PackageSearchDeclaredPackage> {
                        transformers.applyOnEach { registerVersionSerializer() }
                    }
                }
            }
        }
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), Json)

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
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), ModulesState.Loading)

}

@Serializable
sealed interface ModulesState {
    @Serializable
    object Loading : ModulesState
    @Serializable
    data class Ready(val modules: List<PackageSearchModule>) : ModulesState
}