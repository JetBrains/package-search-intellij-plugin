@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.services

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.jetbrains.packagesearch.plugin.PackageSearchModuleBaseTransformerUtils
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchKnownRepositoriesContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchProjectCachesService
import com.jetbrains.packagesearch.plugin.core.utils.fileOpenedFlow
import com.jetbrains.packagesearch.plugin.core.utils.replayOn
import com.jetbrains.packagesearch.plugin.gradle.utils.smartModeFlow
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApplicationCachesService
import com.jetbrains.packagesearch.plugin.utils.WindowedModuleBuilderContext
import com.jetbrains.packagesearch.plugin.utils.getNativeModulesStateFlow
import com.jetbrains.packagesearch.plugin.utils.interval
import com.jetbrains.packagesearch.plugin.utils.startWithNull
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.packagesearch.api.v3.ApiRepository

@Service(Level.PROJECT)
class PackageSearchProjectService(
    override val project: Project,
    override val coroutineScope: CoroutineScope,
) : PackageSearchKnownRepositoriesContext {

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

    private val contextFlow = combine(
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
        )
    }.shareIn(coroutineScope, SharingStarted.Eagerly)

    val moduleData = combine(
        project.getNativeModulesStateFlow(coroutineScope),
        PackageSearchModuleBaseTransformerUtils.extensionsFlow,
        contextFlow
    ) { nativeModules, transformerExtensions, context ->
        transformerExtensions.flatMap { transformer ->
            nativeModules.map { module ->
                transformer.provideModule(context, module).startWithNull()
            }
        }
    }
        .flatMapLatest { combine(it) { it.filterNotNull() } }
        .combine(project.smartModeFlow) { module, isSmartMode ->
            when {
                isSmartMode -> when {
                    module.isEmpty() -> ModulesState.NoModules
                    else -> ModulesState.Ready(module)
                }

                module.isNotEmpty() -> ModulesState.Ready(module)
                else -> {
                    delay(2.seconds)
                    ModulesState.Loading
                }
            }
        }
        .debounce(1.seconds)
        .stateIn(coroutineScope, SharingStarted.Eagerly, ModulesState.Loading)

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
        combine(
            project.fileOpenedFlow,
            moduleDataByBuildFile.map { it.keys }
        ) { openedFiles, buildFiles ->
            openedFiles.filter {
                kotlin.runCatching { it.toNioPath().absolutePathString() in buildFiles }
                    .getOrDefault(false)
            }
        }
            .replayOn(isStableOnlyVersions)
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

sealed interface ModulesState {

    data object Loading : ModulesState
    data class Ready(val moduleData: List<PackageSearchModuleData>) : ModulesState
    data object NoModules : ModulesState
}
