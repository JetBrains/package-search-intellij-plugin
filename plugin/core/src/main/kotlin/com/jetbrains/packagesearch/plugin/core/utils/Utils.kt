@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.core.utils

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.AreaInstance
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.application
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.services.PackageSearchProjectCachesService
import java.nio.file.Path
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage

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

val Project.filesChangedEventFlow: Flow<List<VFileEvent>>
    get() = callbackFlow {
        VirtualFileManager.getInstance().addAsyncFileListener(
            {
                trySend(it.toList())
                null
            },
            this@filesChangedEventFlow
        )
        awaitClose { }
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

val DeclaredDependency.packageId: String
    get() = "maven:${coordinates.groupId}:${coordinates.artifactId}"

fun <T : Any> ExtensionPointName<T>.extensionsFlow(
    areaInstance: AreaInstance? = null,
    initial: Boolean = true,
) = channelFlow {
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

fun <T> ExtensionPointListener(onChange: (T, PluginDescriptor, Boolean) -> Unit) =
    object : ExtensionPointListener<T> {
        override fun extensionAdded(extension: T, pluginDescriptor: PluginDescriptor) {
            onChange(extension, pluginDescriptor, true)
        }

        override fun extensionRemoved(extension: T, pluginDescriptor: PluginDescriptor) {
            onChange(extension, pluginDescriptor, false)
        }
    }

fun StringBuilder.appendEscaped(text: String) =
    StringUtil.escapeToRegexp(text, this)

val IntelliJApplication
    get() = application

fun Application.registryFlow(key: String, defaultValue: Boolean = false) =
    messageBus.flow(RegistryManager.TOPIC) {
        object : RegistryValueListener {
            override fun afterValueChanged(value: RegistryValue) {
                if (value.key == key) trySend(Registry.`is`(key, defaultValue))
            }
        }
    }.onStart { emit(Registry.`is`(key, defaultValue)) }

suspend fun <T> Flow<T>.collectIn(flowCollector: FlowCollector<T>) =
    collect { flowCollector.emit(it) }

val Project.PackageSearchProjectCachesService
    get() = service<PackageSearchProjectCachesService>()

fun ApiPackage.asMavenApiPackage() =
    this as? ApiMavenPackage ?: error(
        "Package $id is of type '${this::class.simpleName}' " +
                "instead of '${ApiMavenPackage::class.simpleName}'"
    )

fun ApiPackage.getIcon(forVersion: String? = null): IconProvider.Icon = when (this) {
    is ApiMavenPackage -> when (versions.all[forVersion] ?: versions.latest) {
        is ApiMavenPackage.GradleVersion -> IconProvider.Icons.GRADLE
        else -> IconProvider.Icons.MAVEN
    }
}

fun <T> Flow<T>.replayOn(vararg replayFlows: Flow<*>) = channelFlow {
    val mutex = Mutex()
    var last: T? = null
    onEach { mutex.withLock { last = it } }
        .onEach { send(it) }
        .launchIn(this)
    merge(*replayFlows).collect { mutex.withLock { last?.let { send(it) } } }
}

val Project.fileOpenedFlow: Flow<List<VirtualFile>>
    get() = flow {
        val buffer: MutableList<VirtualFile> = FileEditorManager.getInstance(this@fileOpenedFlow).openFiles
            .toMutableList()
        emit(buffer.toList())
        messageBus.flow(FileEditorManagerListener.FILE_EDITOR_MANAGER) {
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    trySend(FileEditorEvent.FileOpened(file))
                }

                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    trySend(FileEditorEvent.FileClosed(file))
                }
            }
        }.collect {
            when (it) {
                is FileEditorEvent.FileClosed -> buffer.remove(it.file)
                is FileEditorEvent.FileOpened -> buffer.add(it.file)
            }
            emit(buffer.toList())
        }
    }

internal sealed interface FileEditorEvent {

    val file: VirtualFile

    @JvmInline
    value class FileOpened(override val file: VirtualFile) : FileEditorEvent

    @JvmInline
    value class FileClosed(override val file: VirtualFile) : FileEditorEvent
}