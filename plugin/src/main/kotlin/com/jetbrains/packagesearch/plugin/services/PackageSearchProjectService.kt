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
import com.jetbrains.packagesearch.plugin.core.utils.fileOpenedFlow
import com.jetbrains.packagesearch.plugin.core.utils.replayOn
import com.jetbrains.packagesearch.plugin.core.utils.toolWindowOpenedFlow
import com.jetbrains.packagesearch.plugin.fus.PackageSearchFUSEvent
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApplicationCachesService
import com.jetbrains.packagesearch.plugin.utils.WindowedModuleBuilderContext
import com.jetbrains.packagesearch.plugin.utils.drop
import com.jetbrains.packagesearch.plugin.utils.filterNotNullKeys
import com.jetbrains.packagesearch.plugin.utils.logDebug
import com.jetbrains.packagesearch.plugin.utils.logFUSEvent
import com.jetbrains.packagesearch.plugin.utils.logWarn
import com.jetbrains.packagesearch.plugin.utils.nativeModulesFlow
import com.jetbrains.packagesearch.plugin.utils.startWithNull
import com.jetbrains.packagesearch.plugin.utils.throttle
import com.jetbrains.packagesearch.plugin.utils.timer
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
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

    val isProjectExecutingSyncStateFlow = PackageSearchModuleBaseTransformerUtils.extensionsFlow
        .map { it.map { it.getSyncStateFlow(project) } }
        .flatMapLatest { combine(it) { it.all { it } } }
        .stateIn(coroutineScope, SharingStarted.Lazily, false)

    private val knownRepositoriesStateFlow = timer(12.hours) {
        IntelliJApplication.PackageSearchApplicationCachesService
            .apiPackageCache
            .getKnownRepositories()
            .associateBy { it.id }
    }
        .retry {
            logWarn("${this::class.simpleName}#knownRepositoriesStateFlow", throwable = it)
            true
        }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())

    override val knownRepositories: Map<String, ApiRepository>
        get() = knownRepositoriesStateFlow.value

    private val context = WindowedModuleBuilderContext(
        project = project,
        knownRepositoriesGetter = { knownRepositories },
        packagesCache = IntelliJApplication.PackageSearchApplicationCachesService.apiPackageCache,
        coroutineScope = coroutineScope,
    )

    val packagesBeingDownloadedFlow = context.getLoadingFLow()
        .distinctUntilChanged()
        .onEach { logDebug("${this::class.qualifiedName}#packagesBeingDownloadedFlow") { "$it" } }
        .stateIn(coroutineScope, SharingStarted.Lazily, false)

    private val moduleProvidersList
        get() = combine(
            project.nativeModulesFlow,
            PackageSearchModuleBaseTransformerUtils.extensionsFlow
        ) { nativeModules, transformerExtensions ->
            transformerExtensions.flatMap { transformer ->
                nativeModules.map { module ->
                    with(context) {
                        transformer.provideModule(module).startWithNull()
                    }
                }
            }
        }
            .flatMapLatest { combine(it) { it.filterNotNull() } }
            .drop(1) { it.isEmpty() }
            .debounce(1.seconds)
            .distinctUntilChanged()

    private val restartFlow = restartChannel.consumeAsFlow()
        .shareIn(coroutineScope, SharingStarted.Lazily, 0)

    val modulesStateFlow = restartFlow
        .onStart { emit(Unit) }
        .flatMapLatest { moduleProvidersList }
        .retry(5) {
            logWarn("${this::class.simpleName}#modulesStateFlow", throwable = it)
            true
        }
        .onEach { logDebug("${this::class.qualifiedName}#modulesStateFlow") { "modules.size = ${it.size}" } }
        .stateIn(coroutineScope, SharingStarted.Lazily, emptyList())

    val modulesByBuildFile = modulesStateFlow
        .map { it.associateBy { it.buildFilePath }.filterNotNullKeys() }
        .stateIn(coroutineScope, SharingStarted.Lazily, emptyMap())

    val modulesByIdentity = modulesStateFlow
        .map { it.associateBy { it.identity } }
        .stateIn(coroutineScope, SharingStarted.Lazily, emptyMap())

    private val openedBuildFiles = combine(
        project.fileOpenedFlow,
        modulesByBuildFile.map { it.keys }
    ) { openedFiles, buildFiles ->
        openedFiles.filter { it.toNioPathOrNull()?.let { it in buildFiles } ?: false }
    }
        .shareIn(coroutineScope, SharingStarted.Lazily, 0)

    init {

        stableOnlyStateFlow
            .onEach { logFUSEvent(PackageSearchFUSEvent.OnlyStableToggle(it)) }
            .launchIn(coroutineScope)

        combine(
            openedBuildFiles.map { it.isEmpty() },
            project.toolWindowOpenedFlow("Package Search")
        ) { noOpenedFiles, toolWindowOpened -> noOpenedFiles || !toolWindowOpened }
            .flatMapLatest {
                // if the tool window is not opened and there are no opened build files,
                // we don't need to do anything, and we turn off the isOnlineFlow
                when {
                    it -> IntelliJApplication.PackageSearchApplicationCachesService.isOnlineFlow
                    else -> emptyFlow()
                }
            }
            .distinctUntilChanged()
            .filter { it }
            .throttle(30.minutes)
            .onEach { restart() }
            .retry {
                logWarn("${this::class.simpleName}#isOnlineFlow", throwable = it)
                true
            }
            .launchIn(coroutineScope)


        openedBuildFiles
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
            .retry {
                logWarn("${this::class.simpleName}#fileOpenedFlow", throwable = it)
                true
            }
            .launchIn(coroutineScope)
    }

}

