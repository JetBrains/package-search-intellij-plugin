@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.services

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.jetbrains.packagesearch.plugin.PackageSearchModuleBaseTransformerUtils
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchKnownRepositoriesContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.PackageSearchProjectCachesService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiClientService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApplicationCachesService
import com.jetbrains.packagesearch.plugin.utils.WindowedModuleBuilderContext
import com.jetbrains.packagesearch.plugin.utils.fileOpenedFlow
import com.jetbrains.packagesearch.plugin.utils.getNativeModulesStateFlow
import com.jetbrains.packagesearch.plugin.utils.getRepositories
import com.jetbrains.packagesearch.plugin.utils.interval
import com.jetbrains.packagesearch.plugin.utils.startWithNull
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import org.jetbrains.packagesearch.api.v3.ApiRepository

@Service(Level.PROJECT)
class PackageSearchProjectService(
    override val project: Project,
    override val coroutineScope: CoroutineScope
) : PackageSearchKnownRepositoriesContext {

    val knownRepositoriesStateFlow =
        interval(1.days) {
            getRepositories(
                repoCache = IntelliJApplication.PackageSearchApplicationCachesService.getRepositoryCache(),
                apiClient = IntelliJApplication.PackageSearchApiClientService.client
            )
        }
            .stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())

    override val knownRepositories: Map<String, ApiRepository>
        get() = knownRepositoriesStateFlow.value

    private val contextFlow = knownRepositoriesStateFlow
        .map {
            WindowedModuleBuilderContext(
                project = project,
                knownRepositories = it,
                packagesCache = IntelliJApplication.PackageSearchApplicationCachesService.getApiPackageCache(),
                coroutineScope = coroutineScope,
                projectCaches = project.PackageSearchProjectCachesService.cache.await(),
                applicationCaches = IntelliJApplication.PackageSearchApplicationCachesService.cache.await(),
            )
        }
        .shareIn(coroutineScope, SharingStarted.Eagerly)

    val modules = combine(
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
        .filter { it.isNotEmpty() }
        .map { ModulesState.Ready(it) }
        .debounce(1.seconds)
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), ModulesState.Loading)

    internal val modulesByBuildFile = modules
        .filterIsInstance<ModulesState.Ready>()
        .map { it.modules.associateBy { it.module.buildFilePath } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())


    init {

        val openedBuildFilesFlow = combine(
            project.fileOpenedFlow,
            modules.map { it.modules.mapNotNull { it.module.buildFilePath } }
        ) { openedFiles, buildFiles ->
            openedFiles.filter { it.toNioPath().absolutePathString() in buildFiles }
        }.shareIn(coroutineScope, SharingStarted.Eagerly, 1)

        openedBuildFilesFlow
            .filter { it.isNotEmpty() }
            .take(1)
            .flatMapLatest { modules }
            .flatMapLatest { openedBuildFilesFlow }
            .flatMapMerge { it.asFlow() }
            .debounce(1.seconds)
            .mapNotNull { readAction { PsiManager.getInstance(project).findFile(it) } }
            .onEach { readAction { DaemonCodeAnalyzer.getInstance(project).restart(it) } }
            .flowOn(Dispatchers.EDT)
            .launchIn(coroutineScope)
    }
}

sealed interface ModulesState {

    val modules: List<PackageSearchModuleData>

    data object Loading : ModulesState {
        override val modules: List<PackageSearchModuleData>
            get() = emptyList()
    }

    data class Ready(override val modules: List<PackageSearchModuleData>) : ModulesState
}