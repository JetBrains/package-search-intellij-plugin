@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.core.utils

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.AreaInstance
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.application
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredMavenPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.services.PackageSearchProjectCachesService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository

@RequiresOptIn("This API is internal and you should not use it.")
annotation class PKGSInternalAPI

fun <T : Any, R> MessageBus.flow(
    topic: Topic<T>,
    listener: ProducerScope<R>.() -> T,
) = callbackFlow {
    val connection = simpleConnect()
    connection.subscribe(topic, listener())
    awaitClose { connection.disconnect() }
}

sealed interface FlowEvent<T> {

    @JvmInline
    value class Added<T>(val item: T) : FlowEvent<T>

    @JvmInline
    value class Removed<T>(val item: T) : FlowEvent<T>

    @JvmInline
    value class Initial<T>(val items: List<T>) : FlowEvent<T>
}

fun <T : Any, R> MessageBus.bufferFlow(
    topic: Topic<T>,
    initialValue: (() -> List<R>)? = null,
    listener: ProducerScope<FlowEvent<R>>.() -> T,
) = channelFlow {
    val buffer = mutableSetOf<R>()
    flow(topic, listener).onEach { event ->
        when (event) {
            is FlowEvent.Added -> buffer.add(event.item)
            is FlowEvent.Removed -> buffer.remove(event.item)
            is FlowEvent.Initial -> {
                buffer.clear()
                buffer.addAll(event.items)
            }
        }
        send(buffer.toList())
    }
        .launchIn(this)
    initialValue?.invoke()?.let { send(it) }
    awaitClose()
}

val filesChangedEventFlow: Flow<List<VFileEvent>>
    get() = callbackFlow {
        val disposable = Disposer.newDisposable()
        val fileListener: (events: List<VFileEvent>) -> AsyncFileListener.ChangeApplier = {
            val changeApplier = object : AsyncFileListener.ChangeApplier {
                override fun afterVfsChange() {
                    trySend(it.toList())
                }
            }
            changeApplier
        }
        VirtualFileManager.getInstance().addAsyncFileListener(fileListener, disposable)
        awaitClose { Disposer.dispose(disposable) }
    }

fun VirtualFileListener(action: (VirtualFileEvent) -> Unit) =
    object : VirtualFileListener {
        override fun contentsChanged(event: VirtualFileEvent) {
            action(event)
        }
    }

fun LocalFileSystem.addVirtualFileListener(action: (VirtualFileEvent) -> Unit) =
    VirtualFileListener(action).also { addVirtualFileListener(it) }

fun watchExternalFileChanges(path: Path): Flow<Unit> {
    val fileSystem = LocalFileSystem.getInstance()
    path.parent.toFile().mkdirs()
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

fun <T> Flow<T>.mapUnit(): Flow<Unit> =
    map {}

fun <T : Any> ExtensionPointName<T>.extensionsFlow(areaInstance: AreaInstance? = null) =
    channelFlow {
        val listener = ExtensionPointListener<T> { _, _, _ ->
            trySend(extensionList)
        }
        trySend(extensionList)
        if (areaInstance != null) {
            addExtensionPointListener(areaInstance, listener)
        } else {
            addExtensionPointListener(listener)
        }
        awaitClose { removeExtensionPointListener(listener) }
    }

fun <T> ExtensionPointListener(onChange: (T, PluginDescriptor, Boolean) -> Unit) =
    object : ExtensionPointListener<T> {
        override fun extensionAdded(extension: T, pluginDescriptor: PluginDescriptor) {
            onChange(extension, pluginDescriptor, true)
        }

        override fun extensionRemoved(extension: T, pluginDescriptor: PluginDescriptor) {
            onChange(extension, pluginDescriptor, false)
        }
    }

val IntelliJApplication
    get() = application

fun Application.registryFlow(key: String, defaultValue: Boolean = false) =
    messageBus.flow(RegistryManager.TOPIC) {
        object : RegistryValueListener {
            override fun afterValueChanged(value: RegistryValue) {
                if (value.key == key) trySend(Registry.`is`(key, defaultValue))
            }
        }
    }.withInitialValue(Registry.`is`(key, defaultValue))

val Project.PackageSearchProjectCachesService
    get() = service<PackageSearchProjectCachesService>()

fun ApiPackage.asMavenApiPackage() =
    this as? ApiMavenPackage ?: error(
        "Package $id is of type '${this::class.simpleName}' " +
                "instead of '${ApiMavenPackage::class.simpleName}'"
    )

val ApiPackage.icon: IconProvider.Icon
    get() = when (this) {
        is ApiMavenPackage -> IconProvider.Icons.MAVEN
    }

fun <T> Flow<T>.replayOn(vararg replayFlows: Flow<*>) = channelFlow {
    val mutex = Mutex()
    var last: T? = null
    onEach { mutex.withLock { last = it } }
        .onEach { send(it) }
        .launchIn(this)
    merge(*replayFlows).collect { mutex.withLock { last?.let { send(it) } } }
}

fun Project.toolWindowOpenedFlow(toolWindowId: String): Flow<Boolean> = callbackFlow {
    val manager = ToolWindowManager.getInstance(this@toolWindowOpenedFlow)
    val toolWindow = manager.getToolWindow(toolWindowId)

    // Initial state
    trySend(toolWindow?.isVisible ?: false)

    val listener = object : ToolWindowManagerListener {
        override fun stateChanged(toolWindowManager: ToolWindowManager) {
            trySend(manager.getToolWindow(toolWindowId)?.isVisible ?: false)
        }
    }

    // Register the listener
    val connection = messageBus.connect()
    connection.subscribe(ToolWindowManagerListener.TOPIC, listener)

    // Cleanup on close
    awaitClose { connection.disconnect() }
}

// Usage:
// val toolWindowFlow = project.toolWindowOpenedFlow("YourToolWindowId")
// toolWindowFlow.collect { isOpen ->
//     println("Tool window is open: $isOpen")
// }


val Project.fileOpenedFlow
    get() = messageBus.bufferFlow(
        topic = FileEditorManagerListener.FILE_EDITOR_MANAGER,
        initialValue = { FileEditorManager.getInstance(this).openFiles.toList() }
    ) {
        object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                trySend(FlowEvent.Added(file))
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                trySend(FlowEvent.Removed(file))
            }
        }
    }

val Project.project
    get() = this

val <T : Any> ExtensionPointName<T>.availableExtensionsFlow: FlowWithInitialValue<List<T>>
    get() {
        val extensionPointListener = callbackFlow {
            val buffer = extensions.toMutableSet()
            trySend(buffer.toList())
            val listener = object : ExtensionPointListener<T> {
                override fun extensionAdded(extension: T, pluginDescriptor: PluginDescriptor) {
                    super.extensionAdded(extension, pluginDescriptor)
                    buffer.add(extension)
                    trySend(buffer.toList())
                }

                override fun extensionRemoved(extension: T, pluginDescriptor: PluginDescriptor) {
                    super.extensionRemoved(extension, pluginDescriptor)
                    buffer.remove(extension)
                    trySend(buffer.toList())
                }
            }
            addExtensionPointListener(listener)
            awaitClose { removeExtensionPointListener(listener) }
        }
        return extensionPointListener.withInitialValue(extensions.toList())
    }

class FlowWithInitialValue<T> internal constructor(val initialValue: T, private val delegate: Flow<T>) : Flow<T> {
    override suspend fun collect(collector: FlowCollector<T>) {
        collector.emit(initialValue)
        delegate.collect(collector)
    }
}

fun <T> Flow<T>.withInitialValue(initialValue: T) =
    FlowWithInitialValue(initialValue, this)

val Project.smartModeFlow: FlowWithInitialValue<Boolean>
    get() = messageBus.flow(DumbService.DUMB_MODE) {
        object : DumbService.DumbModeListener {
            override fun enteredDumbMode() {
                trySend(false)
            }

            override fun exitDumbMode() {
                trySend(true)
            }
        }
    }
        .withInitialValue(!DumbService.isDumb(this@smartModeFlow))

fun PackageSearchDeclaredMavenPackage.toUnifiedDependency() =
    UnifiedDependency(groupId, artifactId, declaredVersion?.versionName, declaredScope)

fun ApiMavenRepository.toUnifiedRepository() =
    UnifiedDependencyRepository(id, friendlyName, url)

fun validateRepositoryType(repository: ApiRepository) {
    contract {
        returns() implies (repository is ApiMavenRepository)
    }
    require(repository is ApiMavenRepository) {
        "repository must be ApiMavenRepository instead of ${repository::class.qualifiedName}"
    }
}

fun validateMavenDeclaredPackageType(declaredPackage: PackageSearchDeclaredPackage) {
    contract {
        returns() implies (declaredPackage is PackageSearchDeclaredMavenPackage)
    }
    require(declaredPackage is PackageSearchDeclaredMavenPackage) {
        "declaredPackage must be PackageSearchDeclaredMavenPackage instead " +
                "of ${declaredPackage::class.qualifiedName}"
    }
}

fun validateMavenPackageType(apiPackage: ApiPackage) {
    contract {
        returns() implies (apiPackage is ApiMavenPackage)
    }
    require(apiPackage is ApiMavenPackage) {
        "apiPackage must be ApiMavenPackage instead of ${apiPackage::class.qualifiedName}"
    }
}

val Project.isProjectImportingFlow: MutableStateFlow<Boolean>
    get() = service<ProjectDataImportListenerAdapter.State>()


class ProjectDataImportListenerAdapter(private val project: Project) : ProjectDataImportListener {

    @Service(Service.Level.PROJECT)
    class State : MutableStateFlow<Boolean> by MutableStateFlow(false)

    override fun onImportStarted(projectPath: String?) {
        project.service<State>().value = true
    }

    override fun onFinalTasksFinished(projectPath: String?) {
        project.service<State>().value = false
    }
}

val Module.isSourceSet
    get() = ExternalSystemApiUtil.getExternalModuleType(this) == "sourceSet"

fun <T> Result<T>.suspendSafe() = onFailure { if (it is CancellationException) throw it }

fun Path.isSameFileAsSafe(other: Path): Boolean = kotlin.runCatching { Files.isSameFile(this, other) }
    .getOrDefault(false)
