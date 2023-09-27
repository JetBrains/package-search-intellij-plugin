@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.services

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.observation.Observation
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiManager
import com.jetbrains.packagesearch.plugin.PackageSearchModuleBaseTransformerUtils
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchKnownRepositoriesContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchProjectCachesService
import com.jetbrains.packagesearch.plugin.core.utils.fileOpenedFlow
import com.jetbrains.packagesearch.plugin.core.utils.replayOn
import com.jetbrains.packagesearch.plugin.core.utils.smartModeFlow
import com.jetbrains.packagesearch.plugin.core.utils.withInitialValue
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApplicationCachesService
import com.jetbrains.packagesearch.plugin.utils.WindowedModuleBuilderContext
import com.jetbrains.packagesearch.plugin.utils.interval
import com.jetbrains.packagesearch.plugin.utils.nativeModulesFlow
import com.jetbrains.packagesearch.plugin.utils.startWithNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
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
    internal val isStableOnlyVersions = MutableStateFlow(true)

    val knownRepositoriesStateFlow =
        IntelliJApplication.PackageSearchApplicationCachesService
            .apiPackageCache
            .replayOn(interval(1.days))
            .map { it.getKnownRepositories().associateBy { it.id } }
            .stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())

    override val knownRepositories: Map<String, ApiRepository>
        get() = knownRepositoriesStateFlow.value

    private val arePackagesBeingDownloaded = Channel<Boolean>(onBufferOverflow = DROP_OLDEST)

    private val isLoadingFlow
        get() = combine(
            arePackagesBeingDownloaded.consumeAsFlow(),
            project.smartModeFlow,
        ) { arePackagesBeingDownloaded, isSmartMode ->
            arePackagesBeingDownloaded || isSmartMode
        }

    private val contextFlow
        get() = combine(
            knownRepositoriesStateFlow,
            IntelliJApplication.PackageSearchApplicationCachesService.apiPackageCache
        ) { repositories, client ->
            WindowedModuleBuilderContext(
                project = project,
                knownRepositories = repositories,
                packagesCache = client,
                coroutineScope = coroutineScope,
                projectCaches = project.PackageSearchProjectCachesService.cache,
                applicationCaches = IntelliJApplication.PackageSearchApplicationCachesService.cache,
                isLoadingChannel = arePackagesBeingDownloaded,
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
                    transformer.provideModule(context, module).startWithNull()
                }
            }
        }

    private val restartFlow = restartChannel.consumeAsFlow()
        .shareIn(coroutineScope, SharingStarted.Eagerly, replay = 1)

    private val moduleDataFlow
        get() = restartFlow
            .withInitialValue(Unit)
            .flatMapLatest { moduleProvidersList }
            .onEach { Observation.awaitConfiguration(project) }
            .flatMapLatest { combine(it) { it.filterNotNull() } }
            .distinctUntilChanged()
            .combine(isLoadingFlow) { module, isLoading ->
                when {
                    module.isNotEmpty() -> ModulesState.Ready(module)
                    !isLoading -> ModulesState.NoModules
                    else -> ModulesState.Loading
                }
            }
            .debounce(1.seconds)

    val moduleData = merge(
        moduleDataFlow,
        restartFlow.map { ModulesState.Loading }
    ).stateIn(coroutineScope, SharingStarted.Eagerly, ModulesState.Loading)

    internal val moduleDataByBuildFile = moduleData
        .filterIsInstance<ModulesState.Ready>()
        .map {
            buildMap {
                it.moduleData.forEach {
                    val buildFilePath = it.module.buildFilePath ?: return@forEach
                    put(buildFilePath, it)
                }
            }
        }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())

    init {
        combineTransform(
            project.fileOpenedFlow,
            moduleDataByBuildFile.map { it.keys }
        ) { openedFiles, buildFiles ->
            val knownOpenedBuildFiles = openedFiles
                .filter { it.toNioPathOrNull() in buildFiles }
            if (knownOpenedBuildFiles.isNotEmpty())
                emit(knownOpenedBuildFiles)
        }
            .replayOn(isStableOnlyVersions)
            .flatMapMerge {
                it.asFlow()
            }
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

sealed interface ModulesState {

    data object Loading : ModulesState
    data class Ready(val moduleData: List<PackageSearchModuleData>) : ModulesState
    data object NoModules : ModulesState
}
