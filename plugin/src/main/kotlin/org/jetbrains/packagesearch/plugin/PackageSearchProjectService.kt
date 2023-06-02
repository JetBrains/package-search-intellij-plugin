@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersionWeakCache
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import org.jetbrains.packagesearch.plugin.core.utils.PackageSearchApiClientService
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
                repoCache = IntelliJApplication.PackageSearchCachesService.getRepositoryCache(),
                apiClient = IntelliJApplication.PackageSearchApiClientService.client
            )
        }
            .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), emptyMap())

    val contextFlow = knownRepositoriesStateFlow
        .map {
            WindowedModuleBuilderContext(
                project = project,
                knownRepositories = it,
                packagesCache = IntelliJApplication.PackageSearchCachesService.getApiPackageCache(),
                coroutineScope = coroutineScope,
            )
        }

    val jsonFLow = PackageSearchModuleBaseTransformerUtils.extensionsFlow
        .map { transformers ->
            Json {
                prettyPrint = true
                serializersModule = SerializersModule {
                    contextual(NormalizedVersionWeakCache)
                    polymorphic(PackageSearchModule::class) {
                        transformers.applyOnEach { registerModuleSerializer() }
                    }
                    polymorphic(PackageSearchDeclaredDependency::class) {
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
                transformer.buildModule(context, module)
            }
        }
    }
        .flatMapLatest { combine(it) { it.filterNotNull() } }
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), emptyList())

}

