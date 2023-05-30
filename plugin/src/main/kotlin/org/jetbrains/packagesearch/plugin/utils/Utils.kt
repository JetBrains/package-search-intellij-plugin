@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.utils

import com.intellij.ProjectTopics
import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.AreaInstance
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Function
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import org.dizitart.no2.objects.filters.ObjectFilters
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.client.PackageSearchRemoteApiClient
import org.jetbrains.packagesearch.plugin.PackageSearchProjectService
import org.jetbrains.packagesearch.plugin.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.extensions.PackageSearchModuleTransformer
import org.jetbrains.packagesearch.plugin.extensions.ProjectContext
import org.jetbrains.packagesearch.plugin.nitrite.ApiRepositoryCacheEntry
import org.jetbrains.packagesearch.plugin.nitrite.CoroutineObjectRepository
import org.jetbrains.packagesearch.plugin.nitrite.asEntry
import org.jetbrains.packagesearch.plugin.nitrite.insert
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

fun <T : Any, R> MessageBus.flow(
    topic: Topic<T>,
    listener: ProducerScope<R>.() -> T,
) = callbackFlow {
    val connection = simpleConnect()
    connection.subscribe(topic, listener())
    awaitClose { connection.disconnect() }
}


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

val Project.filesChangedEventFlow: Flow<MutableList<out VFileEvent>>
    get() = messageBus.flow(VirtualFileManager.VFS_CHANGES) {
        object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                trySend(events)
            }
        }
    }

suspend fun PackageSearchModuleTransformer.updateDependency(
    context: ProjectContext,
    module: PackageSearchModule,
    installedPackage: PackageSearchDeclaredDependency,
    knownRepositories: List<ApiRepository>,
    onlyStable: Boolean
) = updateDependencies(context, module, listOf(installedPackage), knownRepositories, onlyStable)

fun PackageSearchModule.getNativeModule(context: ProjectContext) = context.project.modules.find { it.moduleFile?.path == projectDirPath }
    ?: error("Could not find native module for PackageSearchModule $name of type ${this::class.simpleName}")

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
    apiClient: PackageSearchRemoteApiClient,
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

fun VirtualFileListener(action: (VirtualFileEvent) -> Unit) =
    object : VirtualFileListener {
        override fun contentsChanged(event: VirtualFileEvent) {
            action(event)
        }
    }

fun LocalFileSystem.addVirtualFileListener(action: (VirtualFileEvent) -> Unit) =
    VirtualFileListener(action).also { addVirtualFileListener(it) }

fun watchFileChanges(path: Path): Flow<Unit> {
    val fileSystem = LocalFileSystem.getInstance()
    return callbackFlow {
        val watchRequest =
            fileSystem.addRootToWatch(path.parent.toString(), false)
                ?: return@callbackFlow
        val listener = fileSystem.addVirtualFileListener {
            if (it.file.path == path.toString()) {
                trySend(Unit)
            }
        }
        awaitClose {
            fileSystem.removeVirtualFileListener(listener)
            fileSystem.removeWatchedRoot(watchRequest)
        }
    }
}

val Project.packageSearchProjectService
    get() = service<PackageSearchProjectService>()

fun <T> Flow<T>.mapUnit(): Flow<Unit> =
    map {}

fun StringBuilder.appendEscaped(text: String) =
    StringUtil.escapeToRegexp(text, this)


fun <T> ExtensionPointListener(onChange: (T, PluginDescriptor, Boolean) -> Unit) =
    object : ExtensionPointListener<T> {
        override fun extensionAdded(extension: T, pluginDescriptor: PluginDescriptor) {
            onChange(extension, pluginDescriptor, true)
        }

        override fun extensionRemoved(extension: T, pluginDescriptor: PluginDescriptor) {
            onChange(extension, pluginDescriptor, false)
        }
    }

fun <T : Any> ExtensionPointName<T>.extensionsFlow(
    areaInstance: AreaInstance? = null,
    initial: Boolean = true
) =
    channelFlow {
        if (initial) send(extensionList)
        val listener = ExtensionPointListener<T> { _, _, _ ->
            trySend(extensionList)
        }
        if (areaInstance != null) {
            addExtensionPointListener(areaInstance, listener)
        } else {
            addExtensionPointListener(listener)
        }
        awaitClose { removeExtensionPointListener(listener) }
    }

val DeclaredDependency.packageId: String
    get() = "maven:${coordinates.groupId}:${coordinates.artifactId}"