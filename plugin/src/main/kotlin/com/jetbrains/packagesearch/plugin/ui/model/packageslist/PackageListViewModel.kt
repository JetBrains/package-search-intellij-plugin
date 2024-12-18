package com.jetbrains.packagesearch.plugin.ui.model.packageslist

import androidx.compose.foundation.lazy.LazyListState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredRepository
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleEditor
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.replayOn
import com.jetbrains.packagesearch.plugin.fus.PackageSearchFUSEvent
import com.jetbrains.packagesearch.plugin.ui.model.getLatestVersion
import com.jetbrains.packagesearch.plugin.ui.model.hasUpdates
import com.jetbrains.packagesearch.plugin.ui.model.infopanel.InfoPanelViewModel
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent.SetHeaderState.TargetState
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent.SetHeaderState.TargetState.OPEN
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApiClientService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchLogger
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchSettingsService
import com.jetbrains.packagesearch.plugin.utils.logFUSEvent
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiPackageVersion
import org.jetbrains.packagesearch.api.v3.search.buildSearchParameters

@Service(Level.PROJECT)
class PackageListViewModel(
    private val project: Project,
    private val viewModelScope: CoroutineScope,
) {

    private val isOnlineFlow = IntelliJApplication.PackageSearchApiClientService.client.onlineStateFlow

    val isCompactFlow
        get() = project.PackageSearchSettingsService.isInfoPanelOpenFlow

    private val selectedModuleIdsChannel =
        Channel<Set<PackageSearchModule.Identity>>()

    private val selectedModuleIdsSharedFlow = selectedModuleIdsChannel
        .consumeAsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val isOnlineSearchEnabledFlow =
        combine(listOf(selectedModuleIdsSharedFlow.map { it.size == 1 }, isOnlineFlow)) {
            it.all { it }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    private val selectedModulesFlow = combine(
        selectedModuleIdsSharedFlow,
        project.PackageSearchProjectService.modulesByIdentity
    ) { selectedModules, modulesByIdentity ->
        modulesByIdentity.filterKeys { it in selectedModules }.values.toList()
    }
        .shareIn(viewModelScope, SharingStarted.Lazily, 1)

    private val searchQueryMutableStateFlow = MutableStateFlow("")
    val searchQueryStateFlow = searchQueryMutableStateFlow.asStateFlow()

    init {
        searchQueryStateFlow
            .filter { it.isNotEmpty() }
            .debounce(1.seconds)
            .onEach { logFUSEvent(PackageSearchFUSEvent.SearchRequest(it)) }
            .launchIn(viewModelScope)

        selectedModulesFlow
            .onEach { logFUSEvent(PackageSearchFUSEvent.TargetModulesSelected(it)) }
            .launchIn(viewModelScope)
    }

    private val headerCollapsedStatesFlow: MutableStateFlow<Map<PackageListItem.Header.Id, TargetState>> =
        MutableStateFlow(emptyMap())

    private val packagesLoadingMutableStateFlow =
        MutableStateFlow(emptySet<PackageListItem.Package.Id>())

    val packagesLoadingStateFlow = packagesLoadingMutableStateFlow.asStateFlow()

    private val headerLoadingStatesFlow =
        MutableStateFlow(emptySet<PackageListItem.Header.Id.Declared>())

    private val isLoadingChannel = Channel<Boolean>()
    val isLoadingFlow = isLoadingChannel.consumeAsFlow()
        .debounce(50.milliseconds)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private data class ModulesAndSearch(
        val selectedModule: PackageSearchModule,
        val searchQuery: String,
    )

    private val restartSearchChannel = Channel<Unit>()
    private val restartSearchFlow = restartSearchChannel.consumeAsFlow()

    private val searchResultMapFlow: StateFlow<Map<PackageListItem.Header.Id.Remote, Search>> = combine(
        selectedModulesFlow,
        searchQueryStateFlow
    ) { selectedModules, searchQuery ->
        val module = selectedModules.singleOrNull()
        when {
            searchQuery.isNotEmpty() && module != null -> ModulesAndSearch(module, searchQuery)
            else -> null
        }
    }
        .replayOn(restartSearchFlow)
        .mapLatest { data ->
            val map: Map<PackageListItem.Header.Id.Remote, Search> = when (data) {
                null -> emptyMap()
                else -> {
                    isLoadingChannel.send(true)
                    delay(250.milliseconds) // debounce for mapLatest!
                    when (data.selectedModule) {
                        is PackageSearchModule.Base -> data.selectedModule.getSearchQuery(data.searchQuery)
                        is PackageSearchModule.WithVariants -> data.selectedModule.getSearchQueries(data.searchQuery)
                    }
                }
            }
            map
        }
        .onEach { isLoadingChannel.send(false) }
        .modifiedBy(headerCollapsedStatesFlow) { current, change ->
            current.mapValues { (id, value) ->
                when {
                    change[id] == OPEN && value is Search.Query -> value.execute()
                    else -> value
                }
            }
        }
        .modifiedBy(selectedModulesFlow) { current, change ->
            val changeIdentities = change.map { it.identity }
            if (current.keys.any { it.moduleIdentity !in changeIdentities }) {
                emptyMap()
            } else {
                current
            }
        }
        .retry(5)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val selectableLazyListState = SelectableLazyListState(LazyListState())

    val packageListItemsFlow: StateFlow<List<PackageListItem>> =
        combineListChanges(
            modulesFlow = selectedModulesFlow,
            searchResultMapFlow = searchResultMapFlow,
            headerCollapsedStatesFlow = headerCollapsedStatesFlow,
            packagesLoadingStateFlow = packagesLoadingMutableStateFlow,
            headerLoadingStatesFlow = headerLoadingStatesFlow,
            searchQueryFlow = searchQueryStateFlow,
            stableOnlyFlow = project.PackageSearchSettingsService.stableOnlyFlow,
            isOnlineSearchEnabledFlow = isOnlineSearchEnabledFlow,
        )
            .map { change ->
                buildPackageList(
                    isCompact = change.selectedModules.size > 1,
                    onlyStable = change.stableOnly,
                    headerCollapsedStates = change.headerCollapsedStates,
                    packagesLoadingState = change.packagesLoadingState,
                    searchQuery = change.searchQuery,
                    headerLoadingStates = change.headerLoadingStates,
                    modulesMap = change.selectedModules.associateBy { it.identity },
                ) {
                    addFromModules(change.selectedModules)
                    if (change.isOnlineSearchEnabled) {
                        addFromSearch(change.searchResultMap)
                    }
                }
            }
            .retry(5)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private suspend fun PackageSearchModule.Base.getSearchQuery(
        searchQuery: String,
    ): Map<PackageListItem.Header.Id.Remote, Search.Response.Base> {
        val headerId = PackageListItem.Header.Id.Remote.Base(identity)
        val response = Search.Query.Base(
            query = buildSearchParameters {
                this.searchQuery = searchQuery
                packagesType = compatiblePackageTypes
            },
            apis = IntelliJApplication.PackageSearchApiClientService.client,
        ).execute()
        headerCollapsedStatesFlow.update { current ->
            when (headerId) {
                !in current -> current + (headerId to OPEN)
                else -> current
            }
        }
        return mapOf(headerId to response)
    }

    private suspend fun PackageSearchModule.WithVariants.getSearchQueries(
        searchQuery: String,
    ): Map<PackageListItem.Header.Id.Remote, Search> =
        variants.groupByCompatiblePackageTypes()
            .entries
            .sortedBy { (_, variants) ->
                // the group with variant.name == mainVariantName should be first
                when (mainVariantName) {
                    in variants.map { it.name } -> 0
                    else -> 1
                }
            }
            .associate { (packagesType, variants) ->
                val headerId = PackageListItem.Header.Id.Remote.WithVariant(identity, variants.map { it.name })
                val primaryVariantName = variants.first { it.isPrimary }.name
                val attributes = variants.first().attributes.map { it.value }
                val additionalVariants = variants.map { it.name } - primaryVariantName
                val search = when (mainVariantName) {
                    in variants.map { it.name } -> {
                        val response = Search.Query.WithVariants(
                            query = buildSearchParameters {
                                this.searchQuery = searchQuery
                                this.packagesType = packagesType
                            },
                            apis = IntelliJApplication.PackageSearchApiClientService.client,
                            attributes = attributes,
                            primaryVariantName = primaryVariantName,
                            additionalVariants = additionalVariants,
                        ).execute()
                        headerCollapsedStatesFlow.update { current ->
                            when (headerId) {
                                !in current -> current + (headerId to OPEN)
                                else -> current
                            }

                        }
                        response
                    }

                    else -> Search.Query.WithVariants(
                        query = buildSearchParameters {
                            this.searchQuery = searchQuery
                            this.packagesType = packagesType
                        },
                        apis = IntelliJApplication.PackageSearchApiClientService.client,
                        attributes = attributes,
                        primaryVariantName = primaryVariantName,
                        additionalVariants = additionalVariants,
                    )
                }
                headerId to search
            }

    fun clearSearchQuery() {
        viewModelScope.launch {
            searchQueryMutableStateFlow.emit("")
            logFUSEvent(PackageSearchFUSEvent.SearchQueryClear)
        }
    }

    fun setSearchQuery(searchQuery: String) {
        viewModelScope.launch { searchQueryMutableStateFlow.emit(searchQuery) }
    }

    fun setSelectedModules(modules: Set<PackageSearchModule.Identity>) {
        viewModelScope.launch {
            selectedModuleIdsChannel.send(modules)
            packagesLoadingMutableStateFlow.emit(emptySet())
            headerLoadingStatesFlow.emit(emptySet())
        }
    }

    private val json = Json {
        prettyPrint = true
    }

    fun onPackageListItemEvent(event: PackageListItemEvent) {
        viewModelScope.launch {
            when (event) {
                is PackageListItemEvent.EditPackageEvent -> handle(event)
                is PackageListItemEvent.InfoPanelEvent.OnHeaderAttributesClick -> handle(event)
                is PackageListItemEvent.InfoPanelEvent.OnHeaderVariantsClick -> handle(event)
                is PackageListItemEvent.InfoPanelEvent.OnPackageSelected -> handle(event)
                is PackageListItemEvent.InfoPanelEvent.OnPackageDoubleClick -> handle(event)
                is PackageListItemEvent.InfoPanelEvent.OnSelectedPackageClick -> handle(event)
                is PackageListItemEvent.OnPackageAction.GoToSource -> handle(event)
                is PackageListItemEvent.OnPackageAction.Install.Base -> handle(event)
                is PackageListItemEvent.OnPackageAction.Install.WithVariant -> handle(event)
                is PackageListItemEvent.OnPackageAction.Remove -> handle(event)
                is PackageListItemEvent.OnPackageAction.Update -> handle(event)
                is PackageListItemEvent.SetHeaderState -> handle(event)
                is PackageListItemEvent.UpdateAllPackages -> handle(event)
                is PackageListItemEvent.OnRetryPackageSearch -> handle(event)
            }
        }
    }

    @Suppress("unused")
    private suspend fun handle(event: PackageListItemEvent.OnRetryPackageSearch) {
        restartSearchChannel.send(Unit)
    }

    @Suppress("unused")
    private fun handle(event: PackageListItemEvent.InfoPanelEvent.OnHeaderVariantsClick) {
        PackageSearchLogger.logTODO()
        logFUSEvent(PackageSearchFUSEvent.InfoPanelOpened)
    }

    @Suppress("unused")
    private fun handle(event: PackageListItemEvent.InfoPanelEvent.OnPackageDoubleClick) {
        project.PackageSearchSettingsService.isInfoPanelOpenFlow.value = true
        logFUSEvent(PackageSearchFUSEvent.InfoPanelOpened)
    }

    private fun handle(event: PackageListItemEvent.InfoPanelEvent.OnSelectedPackageClick) {
        if (event.eventId is PackageListItem.Package.Id) handle(
            PackageListItemEvent.InfoPanelEvent.OnPackageSelected(event.eventId)
        )
    }

    private fun handle(event: PackageListItemEvent.InfoPanelEvent.OnPackageSelected) {
        val infoPanelViewModel = project.service<InfoPanelViewModel>()
        logFUSEvent(PackageSearchFUSEvent.PackageSelected(event.eventId is PackageListItem.Package.Declared.Id))
        when (event.eventId) {
            is PackageListItem.Package.Remote.Base.Id -> {
                val headerId = PackageListItem.Header.Id.Remote.Base(event.eventId.moduleIdentity)
                val search = searchResultMapFlow.value[headerId] as? Search.Response.Base.Success
                    ?: return
                infoPanelViewModel.setPackage(
                    module = event.eventId.getModule() as? PackageSearchModule.Base ?: return,
                    apiPackage = search.packages.firstOrNull { it.id == event.eventId.packageId } ?: return,
                    packageId = event.eventId
                )
            }

            is PackageListItem.Package.Remote.WithVariant.Id -> {
                val headerId = PackageListItem.Header.Id.Remote.WithVariant(
                    event.eventId.moduleIdentity,
                    event.eventId.headerId.compatibleVariantNames
                )
                val search =
                    searchResultMapFlow.value[headerId] as? Search.Response.WithVariants.Success
                        ?: return
                infoPanelViewModel.setPackage(
                    module = event.eventId.getModule() as? PackageSearchModule.WithVariants ?: return,
                    apiPackage = search.packages.firstOrNull { it.id == event.eventId.packageId } ?: return,
                    primaryVariantName = search.primaryVariantName,
                    packageId = event.eventId
                )
            }

            is PackageListItem.Package.Declared.Id.Base -> {
                val module = event.eventId.getModule() as? PackageSearchModule.Base
                    ?: return
                val declaredPackage = module.declaredDependencies
                    .firstOrNull { it.id == event.eventId.packageId }
                    ?: return
                infoPanelViewModel.setPackage(module, declaredPackage, event.eventId)
            }

            is PackageListItem.Package.Declared.Id.WithVariant -> {
                val module = event.eventId
                    .getModule() as? PackageSearchModule.WithVariants
                    ?: return
                val variant = module.variants[event.eventId.variantName]
                    ?: return
                val declaredPackage = variant.declaredDependencies
                    .firstOrNull { it.id == event.eventId.packageId }
                    ?: return
                infoPanelViewModel.setPackage(module, declaredPackage, event.eventId, event.eventId.variantName)
            }
        }
    }

    private suspend fun handle(actionType: PackageListItemEvent.OnPackageAction.Update) {
        packagesLoadingMutableStateFlow.update { it + actionType.eventId }
        val (module, editor, manager, dependency) =
            actionType.eventId.getDependencyManagers() ?: return
        val newVersion = when {
            project.PackageSearchSettingsService.stableOnlyFlow.value ->
                dependency.remoteInfo?.versions?.latestStable
                    ?: dependency.remoteInfo?.versions?.latest

            else -> dependency.remoteInfo?.versions?.latest
        } ?: return
        logFUSEvent(
            event = PackageSearchFUSEvent.PackageVersionChanged(
                packageIdentifier = dependency.id,
                packageFromVersion = dependency.declaredVersion?.versionName,
                packageTargetVersion = newVersion.normalized.versionName,
                targetModule = module
            )
        )
        editor.editModule {
            editor.addRepositoryIfNeeded(this, newVersion, module.declaredRepositories)
            manager.updateDependency(
                context = this,
                declaredPackage = dependency,
                newVersion = newVersion.normalized.versionName,
                newScope = dependency.declaredScope
            )
        }
    }

    private suspend fun handle(actionType: PackageListItemEvent.OnPackageAction.Remove) {
        packagesLoadingMutableStateFlow.update { it + actionType.eventId }
        val module = actionType.eventId.getModule() ?: return
        module.editModule {
            when (module) {
                is PackageSearchModule.Base -> {
                    val declaredPackage = module.declaredDependencies
                        .firstOrNull { it.id == actionType.eventId.packageId }
                        ?: return@editModule
                    logFUSEvent(
                        event = PackageSearchFUSEvent.PackageRemoved(
                            packageIdentifier = actionType.eventId.packageId,
                            packageVersion = declaredPackage.declaredVersion?.versionName,
                            targetModule = module
                        )
                    )
                    module.removeDependency(this, declaredPackage)
                }

                is PackageSearchModule.WithVariants -> {
                    val eventId = actionType
                        .eventId as? PackageListItem.Package.Declared.Id.WithVariant
                        ?: return@editModule
                    val variant = module.variants[eventId.variantName]
                        ?: return@editModule
                    val declaredPackage = variant.declaredDependencies
                        .firstOrNull { it.id == eventId.packageId }
                        ?: return@editModule
                    logFUSEvent(
                        event = PackageSearchFUSEvent.PackageRemoved(
                            packageIdentifier = actionType.eventId.packageId,
                            packageVersion = declaredPackage.declaredVersion?.versionName,
                            targetModule = module
                        )
                    )
                    variant.removeDependency(this, declaredPackage)
                }
            }
        }
    }

    private suspend fun handle(actionType: PackageListItemEvent.OnPackageAction.Install.WithVariant) {
        packagesLoadingMutableStateFlow.update { it + actionType.eventId }
        val module = actionType.eventId
            .getModule() as? PackageSearchModule.WithVariants
            ?: return
        val variant = module.variants[actionType.selectedVariantName]
            ?: return
        val search = searchResultMapFlow
            .value[actionType.headerId] as? Search.Response.WithVariants.Success
            ?: return
        val apiPackage = search.packages
            .firstOrNull { it.id == actionType.eventId.packageId }
            ?: return
        logFUSEvent(
            event = PackageSearchFUSEvent.PackageInstalled(
                packageIdentifier = apiPackage.id,
                targetModule = module
            )
        )
        installDependency(
            manager = variant,
            updater = module,
            apiPackage = apiPackage,
            installedRepositories = module.declaredRepositories,
            scope = variant.defaultScope
        )
    }

    private suspend fun handle(actionType: PackageListItemEvent.OnPackageAction.Install.Base) {
        packagesLoadingMutableStateFlow.update { it + actionType.eventId }
        val module = actionType.eventId
            .getModule() as? PackageSearchModule.Base
            ?: return
        val search =
            searchResultMapFlow.value[actionType.headerId] as? Search.Response.Base.Success ?: return
        val apiPackage = search.packages
            .firstOrNull { it.id == actionType.eventId.packageId }
            ?: return
        logFUSEvent(PackageSearchFUSEvent.PackageInstalled(apiPackage.id, module))
        installDependency(
            manager = module,
            updater = module,
            apiPackage = apiPackage,
            installedRepositories = module.declaredRepositories,
            scope = module.defaultScope
        )
    }

    private suspend fun installDependency(
        manager: PackageSearchDependencyManager,
        updater: PackageSearchModuleEditor,
        installedRepositories: List<PackageSearchDeclaredRepository>,
        apiPackage: ApiPackage,
        scope: String?,
    ) = updater.editModule {

        val selectedVersion = when {
            project.PackageSearchSettingsService.stableOnlyFlow.value ->
                apiPackage.versions.latestStable ?: apiPackage.versions.latest

            else -> apiPackage.versions.latest
        }
        updater.addRepositoryIfNeeded(this, selectedVersion, installedRepositories)
        manager.addDependency(
            context = this,
            apiPackage = apiPackage,
            selectedVersion = selectedVersion.normalized.versionName,
            selectedScope = scope
        )
    }

    private fun PackageSearchModuleEditor.addRepositoryIfNeeded(
        context: EditModuleContext,
        selectedVersion: ApiPackageVersion,
        installedRepositories: List<PackageSearchDeclaredRepository>,
    ) {
        if (project.PackageSearchSettingsService.installRepositoryIfNeededFlow.value) {
            val apiRepository =
                selectedVersion.repositoryIds
                    .firstNotNullOfOrNull { project.PackageSearchProjectService.knownRepositories[it] }

            if (apiRepository != null && apiRepository.url !in installedRepositories.map { it.url }) {
                addRepository(context, apiRepository)
            }
        }
    }

    private suspend fun handle(actionType: PackageListItemEvent.OnPackageAction.GoToSource) {
        val module = actionType.eventId.getModule()
        val dependency = when (module) {
            is PackageSearchModule.Base -> module.declaredDependencies
                .firstOrNull { it.id == actionType.eventId.packageId }

            is PackageSearchModule.WithVariants -> {
                val eventId = actionType
                    .eventId as? PackageListItem.Package.Declared.Id.WithVariant
                    ?: return
                val variant = module.variants[eventId.variantName]
                    ?: return
                variant.declaredDependencies.firstOrNull { it.id == eventId.packageId }
            }

            null -> return
        } ?: return
        logFUSEvent(PackageSearchFUSEvent.GoToSource(module, dependency.id))
        val buildFile = module.buildFilePath
            ?.let { LocalFileSystem.getInstance().findFileByNioFile(it) }
            ?: return
        withContext(Dispatchers.Main) {
            FileEditorManager
                .getInstance(project)
                .openFileEditor(
                    OpenFileDescriptor(
                        project,
                        buildFile,
                        dependency.declarationIndexes.declarationStartIndex
                    ),
                    true
                )
        }
    }

    private fun handle(event: PackageListItemEvent.SetHeaderState) {
        headerCollapsedStatesFlow.update {
            it + (event.eventId to event.targetState)
        }
    }

    private suspend fun handle(event: PackageListItemEvent.EditPackageEvent) {
        packagesLoadingMutableStateFlow.update { it + event.eventId }
        val (module, editor, manager, dependency) =
            event.eventId.getDependencyManagers() ?: return
        runCatching {
            editor.editModule {
                when (event) {
                    is PackageListItemEvent.EditPackageEvent.SetPackageScope -> {
                        logFUSEvent(
                            event = PackageSearchFUSEvent.PackageScopeChanged(
                                packageIdentifier = dependency.id,
                                scopeFrom = dependency.declaredScope,
                                scopeTo = event.scope,
                                targetModule = module
                            )
                        )
                        manager.updateDependency(
                            context = this,
                            declaredPackage = dependency,
                            newVersion = dependency.declaredVersion?.versionName,
                            newScope = event.scope
                        )
                    }

                    is PackageListItemEvent.EditPackageEvent.SetPackageVersion -> {
                        logFUSEvent(
                            event = PackageSearchFUSEvent.PackageVersionChanged(
                                packageIdentifier = dependency.id,
                                packageFromVersion = dependency.declaredVersion?.versionName,
                                packageTargetVersion = event.version,
                                targetModule = module
                            )
                        )
                        if (project.PackageSearchSettingsService.installRepositoryIfNeededFlow.value) {
                            dependency.remoteInfo?.versions?.all
                                ?.firstOrNull { it.normalizedVersion.versionName == event.version }
                                ?.repositoryIds
                                ?.firstNotNullOfOrNull { project.PackageSearchProjectService.knownRepositories[it] }
                                ?.takeIf { it.url !in module.declaredRepositories.map { it.url } }
                                ?.let { editor.addRepository(this, it) }
                        }
                        manager.updateDependency(
                            context = this,
                            declaredPackage = dependency,
                            newVersion = event.version,
                            newScope = dependency.declaredScope
                        )
                    }

                    is PackageListItemEvent.EditPackageEvent.SetVariant -> {
                        logFUSEvent(
                            event = PackageSearchFUSEvent.PackageVariantChanged(
                                packageIdentifier = dependency.id,
                                targetModule = module
                            )
                        )
                        PackageSearchLogger.logTODO()
                    }
                }
            }
        }
            .onFailure {
                PackageSearchLogger.logWarn("Failed to set scope for package:\n${json.encodeToString(event)}", it)
            }
    }

    private fun handle(event: PackageListItemEvent.InfoPanelEvent.OnHeaderAttributesClick) {
        logFUSEvent(PackageSearchFUSEvent.HeaderAttributesClick(event.eventId is PackageListItem.Header.Id.Remote))
        val infoPanelViewModel = project.service<InfoPanelViewModel>()

        val module = event.eventId.getModule() as? PackageSearchModule.WithVariants ?: return

        when (event) {
            is PackageListItemEvent.InfoPanelEvent.OnHeaderAttributesClick.DeclaredHeaderAttributesClick -> {
                val attributes = module.variants[event.variantName]?.attributes ?: return
                infoPanelViewModel.setDeclaredHeaderAttributes(event.variantName, attributes = attributes)
            }

            is PackageListItemEvent.InfoPanelEvent.OnHeaderAttributesClick.SearchHeaderWithVariantsAttributesClick -> {

                val variants = event.eventId
                    .compatibleVariantNames
                    .mapNotNull { module.variants[it] }

                val defaultVariant = variants.firstOrNull { it.isPrimary }?.name
                    ?: variants.firstOrNull()?.name
                    ?: return

                val attributes = variants.firstOrNull()
                    ?.attributes
                    ?: return

                infoPanelViewModel.setSearchHeaderAttributes(
                    defaultVariant = defaultVariant,
                    additionalVariants = variants.map { it.name } - defaultVariant,
                    attributes = attributes
                )
            }
        }


        project.PackageSearchSettingsService.isInfoPanelOpenFlow.value = true

    }

    @Suppress("unused")
    private suspend fun handle(event: PackageListItemEvent.EditPackageEvent.SetVariant) {
        val module = event.eventId
            .getModule() as? PackageSearchModule.WithVariants
            ?: return
        val variant = module.variants[event.eventId.variantName]
            ?: return
        val declaredPackage = variant.declaredDependencies
            .firstOrNull { it.id == event.eventId.packageId }
            ?: return
        val newVariant = module.variants[event.selectedVariantName]
            ?: return
        module.editModule {
            variant.removeDependency(this, declaredPackage)
//            newVariant.addDependency(
//                apiPackage = declaredPackage,
//                selectedVersion = declaredPackage.declaredVersion?.versionName,
//                selectedScope = declaredPackage.declaredScope
//            )
        }
    }

    private suspend fun handle(event: PackageListItemEvent.UpdateAllPackages) {
        headerLoadingStatesFlow.update { it + event.eventId }
        logFUSEvent(PackageSearchFUSEvent.UpgradeAll)
        val onlyStable = project.PackageSearchSettingsService.stableOnlyFlow.value
        when (val module = event.eventId.getModule()) {
            is PackageSearchModule.Base -> {
                val packagesToUpdate = module.declaredDependencies
                    .filter { it.hasUpdates(onlyStable) }
                    .filter { it.matchesSearchQuery(searchQueryStateFlow.value) }
                val listIds = packagesToUpdate.map {
                    PackageListItem.Package.Declared.Id.Base(module.identity, it.id)
                }.toSet()
                packagesLoadingMutableStateFlow.update { it + listIds }
                runCatching {
                    module.editModule {
                        val repositoriesToAdd = packagesToUpdate
                            .mapNotNull {
                                val version = when {
                                    onlyStable -> it.remoteInfo?.versions?.latestStable
                                        ?: it.remoteInfo?.versions?.latest

                                    else -> it.remoteInfo?.versions?.latest
                                }
                                module.updateDependency(
                                    context = this,
                                    declaredPackage = it,
                                    newVersion = version?.normalized?.versionName ?: it.declaredVersion?.versionName,
                                    newScope = it.declaredScope
                                )
                                version?.repositoryIds
                                    ?.firstNotNullOfOrNull { project.PackageSearchProjectService.knownRepositories[it] }
                                    ?.takeIf { it.url !in module.declaredRepositories.map { it.url } }
                            }
                            .toSet()
                        if (repositoriesToAdd.isNotEmpty() && project.PackageSearchSettingsService.installRepositoryIfNeededFlow.value) {
                            repositoriesToAdd.forEach { module.addRepository(this, it) }
                        }
                    }
                }
                    .onFailure {
                        PackageSearchLogger.logWarn("Failed to update packages:\n${json.encodeToString(event)}", it)
                    }
            }

            is PackageSearchModule.WithVariants -> {
                val packagesToUpdate =
                    module.variants.values.flatMap { variant ->
                        variant.declaredDependencies
                            .filter { it.hasUpdates(onlyStable) }
                            .filter { it.matchesSearchQuery(searchQueryStateFlow.value) }
                            .map { variant to it }
                    }
                val listIds = packagesToUpdate
                    .map { (variant, declaredPackage) ->
                        PackageListItem.Package.Declared.Id.WithVariant(
                            moduleIdentity = module.identity,
                            packageId = variant.name,
                            variantName = declaredPackage.id
                        )
                    }.toSet()
                packagesLoadingMutableStateFlow.update { it + listIds }
                runCatching {
                    module.editModule {
                        packagesToUpdate
                            .forEach { (variant, declaredPackage) ->
                                variant.updateDependency(
                                    context = this,
                                    declaredPackage = declaredPackage,
                                    newVersion = declaredPackage.getLatestVersion(onlyStable)?.versionName,
                                    newScope = declaredPackage.declaredScope
                                )
                            }
                    }
                }
                    .onFailure {
                        PackageSearchLogger.logWarn("Failed to update packages:\n${json.encodeToString(event)}", it)
                    }
            }

            null -> return
        }
    }

    private fun PackageListItem.Id.getModule() =
        project.PackageSearchProjectService
            .modulesByIdentity
            .value[moduleIdentity]

    private fun PackageListItem.Package.Declared.Id.getDependencyManagers(): PackageSearchDependencyHandlers? {
        val modulesById = project.PackageSearchProjectService
            .modulesByIdentity.value
        return when (this) {
            is PackageListItem.Package.Declared.Id.Base -> {
                val module = getModule() as? PackageSearchModule.Base ?: return null
                PackageSearchDependencyHandlers(
                    module = module,
                    declaredPackage = module.declaredDependencies
                        .firstOrNull { it.id == packageId }
                        ?: return null,
                )
            }

            is PackageListItem.Package.Declared.Id.WithVariant -> {
                val withVariants =
                    modulesById[moduleIdentity] as? PackageSearchModule.WithVariants
                        ?: return null
                val variant = withVariants.variants[variantName]
                    ?: return null
                PackageSearchDependencyHandlers(
                    module = withVariants,
                    modifier = withVariants,
                    manager = variant,
                    declaredPackage = variant.declaredDependencies
                        .firstOrNull { it.id == packageId }
                        ?: return null,
                )
            }
        }
    }

}
