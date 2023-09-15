@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.utils

import com.intellij.ProjectTopics
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.util.Function
import com.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import com.jetbrains.packagesearch.plugin.core.nitrite.insert
import com.jetbrains.packagesearch.plugin.core.utils.flow
import io.ktor.client.plugins.logging.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import org.dizitart.no2.objects.filters.ObjectFilters
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient


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

fun interval(
    interval: Duration,
    emitOnStart: Boolean = false,
) = flow {
    if (emitOnStart) {
        emit(Unit)
    }
    while (true) {
        delay(interval)
        emit(Unit)
    }
}

// todo the _id must be a Long
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
            .also { repoCache.insert(it.asCacheEntry()) }
            .associateBy { it.id }

typealias NativeModules = List<Module>
typealias NativeModule = Module

inline fun <reified T : Any> SerializersModuleBuilder.polymorphic(
    baseSerializer: KSerializer<T>? = null,
    builderAction: PolymorphicModuleBuilder<T>.() -> Unit,
) = polymorphic(T::class, baseSerializer, builderAction)


fun <T> Flow<T?>.startWithNull() = onStart { emit(null) }

@Suppress("FunctionName")
fun KtorDebugLogger() = object : Logger {
    override fun log(message: String) = logDebug(message)
}



