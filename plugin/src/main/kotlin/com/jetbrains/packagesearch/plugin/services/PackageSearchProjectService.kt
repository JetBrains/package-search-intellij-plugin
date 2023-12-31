@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.services

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiManager
import com.jetbrains.packagesearch.plugin.PackageSearchModuleBaseTransformerUtils
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchKnownRepositoriesContext
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchProjectCachesService
import com.jetbrains.packagesearch.plugin.core.utils.fileOpenedFlow
import com.jetbrains.packagesearch.plugin.core.utils.replayOn
import com.jetbrains.packagesearch.plugin.core.utils.withInitialValue
import com.jetbrains.packagesearch.plugin.fus.logOnlyStableToggle
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApplicationCachesService
import com.jetbrains.packagesearch.plugin.utils.WindowedModuleBuilderContext
import com.jetbrains.packagesearch.plugin.utils.filterNotNullKeys
import com.jetbrains.packagesearch.plugin.utils.logWarn
import com.jetbrains.packagesearch.plugin.utils.nativeModulesFlow
import com.jetbrains.packagesearch.plugin.utils.startWithNull
import com.jetbrains.packagesearch.plugin.utils.timer
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.packagesearch.api.v3.ApiRepository

@Service(Level.PROJECT)
class PackageSearchProjectService(
    override val project: Project,
    override val coroutineScope: CoroutineScope,
) : PackageSearchKnownRepositoriesContext {

    private val restartChannel = Channel<Unit>()

    fun restart() {
        restartChannel.trySend(Unit)
    }

    // Todo SAVE
    internal val stableOnlyStateFlow = MutableStateFlow(true)

    val knownRepositoriesStateFlow = timer(12.hours) {
        IntelliJApplication.PackageSearchApplicationCachesService
            .apiPackageCache
            .getKnownRepositories()
            .associateBy { it.id }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())

    override val knownRepositories: Map<String, ApiRepository>
        get() = knownRepositoriesStateFlow.value

    private val packagesBeingDownloadedChannel = Channel<Boolean>(onBufferOverflow = DROP_OLDEST)
    val packagesBeingDownloadedFlow = packagesBeingDownloadedChannel.consumeAsFlow()
        .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    private val contextFlow
        get() = knownRepositoriesStateFlow.map { repositories ->
            WindowedModuleBuilderContext(
                project = project,
                knownRepositories = repositories,
                packagesCache = IntelliJApplication.PackageSearchApplicationCachesService.apiPackageCache,
                coroutineScope = coroutineScope,
                projectCaches = project.PackageSearchProjectCachesService.cache,
                applicationCaches = IntelliJApplication.PackageSearchApplicationCachesService.cache,
                isLoadingChannel = packagesBeingDownloadedChannel,
            )
        }

    private val moduleProvidersList
        get() = combine(
            project.nativeModulesFlow,
            PackageSearchModuleBaseTransformerUtils.extensionsFlow,
            contextFlow
        ) { nativeModules, transformerExtensions, context ->
            transformerExtensions.flatMap { transformer ->
                nativeModules.map { module ->
                    with(context) {
                        transformer.provideModule(module).startWithNull()
                    }
                }
            }
        }
            .flatMapLatest { combine(it) { it.filterNotNull() } }
            .distinctUntilChanged()

    private val restartFlow = restartChannel.consumeAsFlow()
        .shareIn(coroutineScope, SharingStarted.Eagerly, replay = 1)

    private var counter = 0
    private val counterMutex = Mutex()

    private suspend fun restartWithCounter() = counterMutex.withLock {
        if (counter++ < 3) {
            restart()
        }
    }

    private suspend fun resetCounter() = counterMutex.withLock { counter = 0 }

    val modulesStateFlow = restartFlow
        .withInitialValue(Unit)
        .flatMapLatest { moduleProvidersList }
        .catch {
            logWarn("${this::class.simpleName}#modulesStateFlow", throwable = it)
            restartWithCounter()
        }
        .onEach { resetCounter() }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    val modulesByBuildFile = modulesStateFlow
        .map { it.associateBy { it.buildFilePath }.filterNotNullKeys() }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())

    val modulesByIdentity = modulesStateFlow
        .map { it.associateBy { it.identity } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())

    init {

        stableOnlyStateFlow
            .onEach { logOnlyStableToggle(it) }
            .launchIn(coroutineScope)

        IntelliJApplication.PackageSearchApplicationCachesService
            .isOnlineFlow
            .filter { it }
            .onEach { restart() }
            .launchIn(coroutineScope)

        combine(
            project.fileOpenedFlow,
            modulesByBuildFile.map { it.keys }
        ) { openedFiles, buildFiles ->
            openedFiles.filter { it.toNioPathOrNull()?.let { it in buildFiles } ?: false }
        }
            .filter { it.isNotEmpty() }
            .replayOn(stableOnlyStateFlow)
            .flatMapMerge { it.asFlow() }
            .debounce(1.seconds)
            .onEach {
                readAction {
                    PsiManager.getInstance(project)
                        .findFile(it)
                        ?.let { DaemonCodeAnalyzer.getInstance(project).restart(it) }
                }
            }
            .launchIn(coroutineScope)
    }

}


