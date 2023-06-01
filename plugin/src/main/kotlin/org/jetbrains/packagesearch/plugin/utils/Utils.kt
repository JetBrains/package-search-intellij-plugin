@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.utils

import com.intellij.ProjectTopics
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.Function
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import org.dizitart.no2.objects.filters.ObjectFilters
import org.jetbrains.packagesearch.client.PackageSearchApiClient
import org.jetbrains.packagesearch.plugin.PackageSearchApiClientService
import org.jetbrains.packagesearch.plugin.PackageSearchApiEndpointsService
import org.jetbrains.packagesearch.plugin.core.nitrite.PackageSearchCaches
import org.jetbrains.packagesearch.plugin.PackageSearchProjectService
import org.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import org.jetbrains.packagesearch.plugin.core.utils.flow
import org.jetbrains.packagesearch.plugin.core.nitrite.ApiRepositoryCacheEntry
import org.jetbrains.packagesearch.plugin.core.nitrite.CoroutineObjectRepository
import org.jetbrains.packagesearch.plugin.core.nitrite.asEntry
import org.jetbrains.packagesearch.plugin.core.nitrite.insert
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days




internal val Project.nativeModules
    get() = ModuleManager.getInstance(this).modules.toList()

internal fun Project.getNativeModulesStateFlow(
    coroutineScope: CoroutineScope,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(),
) = nativeModulesFlow.stateIn(coroutineScope, sharingStarted, nativeModules)

internal val Project.nativeModulesFlow: Flow<NativeModules>
    get() = messageBus.flow(ProjectTopics.MODULES) {
        object : ModuleListener {
            override fun modulesAdded(project: Project, modules: NativeModules) {
                trySend(nativeModules)
            }

            override fun moduleRemoved(project: Project, module: Module) {
                trySend(nativeModules)
            }

            override fun modulesRenamed(
                project: Project,
                modules: MutableList<out Module>,
                oldNameProvider: Function<in Module, String>,
            ) {
                trySend(nativeModules)
            }
        }
    }



internal fun Project.asContext() =
    SimpleProjectContext(this)

@JvmInline
value class SimpleProjectContext(override val project: Project) : ProjectContext

fun <T> interval(
    interval: Duration,
    emitOnStart: Boolean = true,
    function: suspend () -> T,
) = flow {
    if (emitOnStart) {
        emit(function())
    }
    while (true) {
        delay(interval)
        emit(function())
    }
}

suspend fun getRepositories(
    repoCache: CoroutineObjectRepository<ApiRepositoryCacheEntry>,
    apiClient: PackageSearchApiClient,
    expireDuration: Duration = 14.days,
) =
    repoCache.find(ObjectFilters.eq("_id", "knownRepositories"))
        .filter { it.lastUpdate + expireDuration < Clock.System.now() }
        .map { it.data }
        .firstOrNull()
        ?.associateBy { it.id }
        ?: apiClient.getKnownRepositories()
            .also { repoCache.insert(it.asEntry()) }
            .associateBy { it.id }

typealias NativeModules = List<Module>



val Project.PackageSearchService
    get() = service<PackageSearchProjectService>()


fun Application.registryStateFlow(scope: CoroutineScope, key: String, defaultValue: Boolean = false) =
    messageBus.flow(RegistryManager.TOPIC) {
        object : RegistryValueListener {
            override fun afterValueChanged(value: RegistryValue) {
                if (value.key == key) trySend(Registry.`is`(key, defaultValue))
            }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(), Registry.`is`(key, defaultValue))

val IntelliJApplication
    get() = application

val Application.PackageSearchCachesService
    get() = service<PackageSearchCaches>()

val Application.PackageSearchApiEndpointsService
    get() = service<PackageSearchApiEndpointsService>()

val Application.PackageSearchApiClientService
    get()= service<PackageSearchApiClientService>()