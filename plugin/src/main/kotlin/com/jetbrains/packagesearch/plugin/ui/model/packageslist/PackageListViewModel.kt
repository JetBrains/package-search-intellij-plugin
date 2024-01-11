package com.jetbrains.packagesearch.plugin.ui.model.packageslist

import androidx.compose.foundation.lazy.LazyListState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleEditor
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.fus.logGoToSource
import com.jetbrains.packagesearch.plugin.fus.logHeaderAttributesClick
import com.jetbrains.packagesearch.plugin.fus.logHeaderVariantsClick
import com.jetbrains.packagesearch.plugin.fus.logInfoPanelOpened
import com.jetbrains.packagesearch.plugin.fus.logPackageInstalled
import com.jetbrains.packagesearch.plugin.fus.logPackageRemoved
import com.jetbrains.packagesearch.plugin.fus.logPackageScopeChanged
import com.jetbrains.packagesearch.plugin.fus.logPackageSelected
import com.jetbrains.packagesearch.plugin.fus.logPackageVariantChanged
import com.jetbrains.packagesearch.plugin.fus.logPackageVersionChanged
import com.jetbrains.packagesearch.plugin.fus.logSearchQueryClear
import com.jetbrains.packagesearch.plugin.fus.logSearchRequest
import com.jetbrains.packagesearch.plugin.fus.logTargetModuleSelected
import com.jetbrains.packagesearch.plugin.fus.logUpgradeAll
import com.jetbrains.packagesearch.plugin.ui.model.ToolWindowViewModel
import com.jetbrains.packagesearch.plugin.ui.model.getLatestVersion
import com.jetbrains.packagesearch.plugin.ui.model.hasUpdates
import com.jetbrains.packagesearch.plugin.ui.model.infopanel.InfoPanelViewModel
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent.SetHeaderState.TargetState
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent.SetHeaderState.TargetState.OPEN
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApplicationCachesService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.utils.logTODO
import com.jetbrains.packagesearch.plugin.utils.logWarn
import com.jetbrains.packagesearch.plugin.utils.searchPackages
import kotlin.time.Duration.Companion.milliseconds
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
import org.jetbrains.packagesearch.api.v3.search.buildSearchParameters

@Service(Level.PROJECT)
class PackageListViewModel(
    private val project: Project,
    private val viewModelScope: CoroutineScope,
) {

    private val isOnline
        get() = IntelliJApplication.PackageSearchApplicationCachesService
            .isOnlineFlow

    val isCompactFlow
        get() = project.service<ToolWindowViewModel>().isInfoPanelOpen

    private val selectedModuleIdsChannel =
        Channel<Set<PackageSearchModule.Identity>>()

    private val selectedModuleIdsSharedFlow = selectedModuleIdsChannel
        .consumeAsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val isOnlineSearchEnabledFlow =
        combine(listOf(selectedModuleIdsSharedFlow.map { it.size == 1 }, isOnline)) {
            it.all { it }
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val selectedModulesFlow = combine(
        selectedModuleIdsSharedFlow,
        project.PackageSearchProjectService.modulesByIdentity
    ) { selectedModules, modulesByIdentity ->
        modulesByIdentity.filterKeys { it in selectedModules }.values.toList()
    }
        .shareIn(viewModelScope, SharingStarted.Lazily)

    private val searchQueryMutableStateFlow = MutableStateFlow("")
    val searchQueryStateFlow = searchQueryMutableStateFlow.asStateFlow()

    init {
        searchQueryStateFlow
            .filter { it.isNotEmpty() }
            .onEach { logSearchRequest(it) }
            .launchIn(viewModelScope)

        selectedModulesFlow
            .onEach { logTargetModuleSelected(it) }
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
        .mapLatest { data ->
            when (data) {
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
        }
        .onEach { isLoadingChannel.send(false) }
        .modifiedBy(headerCollapsedStatesFlow) { current: Map<PackageListItem.Header.Id.Remote, Search>, change ->
            current.mapValues { (id, value) ->
                when {
                    change[id] == OPEN && value is Search.Query -> value.execute()
                    else -> value
                }
            }
        }
        .modifiedBy(selectedModulesFlow) { current: Map<PackageListItem.Header.Id.Remote, Search>, change ->
            val changeIdentities = change.map { it.identity }
            if (current.keys.any { it.moduleIdentity !in changeIdentities }) {
                emptyMap()
            } else {
                current
            }
        }
        .retry()
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
            stableOnlyFlow = project.PackageSearchProjectService.stableOnlyStateFlow,
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
            .retry()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private suspend fun PackageSearchModule.Base.getSearchQuery(
        searchQuery: String,
    ): Map<PackageListItem.Header.Id.Remote, Search.Results.Base> {
        val headerId = PackageListItem.Header.Id.Remote.Base(identity)
        val results = Search.Results.Base(
            packages = IntelliJApplication.PackageSearchApplicationCachesService.apiPackageCache
                .searchPackages(buildSearchParameters {
                    this.searchQuery = searchQuery
                    packagesType = compatiblePackageTypes
                }),
        )
        headerCollapsedStatesFlow.update { current ->
            when (headerId) {
                !in current -> current + (headerId to OPEN)
                else -> current
            }
        }
        return mapOf(headerId to results)
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
            }.associate { (packagesType, variants) ->
                val headerId = PackageListItem.Header.Id.Remote.WithVariant(identity, variants.map { it.name })
                val primaryVariantName = variants.first { it.isPrimary }.name
                val attributes = variants.first().attributes.map { it.value }
                val additionalVariants = variants.map { it.name } - primaryVariantName
                val search: Search = when (mainVariantName) {
                    in variants.map { it.name } -> {
                        val results = Search.Results.WithVariants(
                            packages = IntelliJApplication.PackageSearchApplicationCachesService
                                .apiPackageCache
                                .searchPackages {
                                    this.searchQuery = searchQuery
                                    this.packagesType = packagesType
                                },
                            attributes = attributes,
                            primaryVariantName = primaryVariantName,
                            additionalVariants = additionalVariants,
                        )
                        headerCollapsedStatesFlow.update { current ->
                            when (headerId) {
                                !in current -> current + (headerId to OPEN)
                                else -> current
                            }

                        }
                        results
                    }

                    else -> Search.Query.WithVariants(
                        query = buildSearchParameters {
                            this.searchQuery = searchQuery
                            this.packagesType = packagesType
                        },
                        apis = IntelliJApplication.PackageSearchApplicationCachesService.apiPackageCache,
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
            logSearchQueryClear()
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
            }
        }
    }

    private fun handle(event: PackageListItemEvent.InfoPanelEvent.OnHeaderVariantsClick) {
        logTODO()
        logHeaderVariantsClick()
    }

    private suspend fun handle(event: PackageListItemEvent.InfoPanelEvent.OnPackageDoubleClick) {
        project.service<ToolWindowViewModel>().isInfoPanelOpen.emit(true)
        logInfoPanelOpened()
    }

    private fun handle(event: PackageListItemEvent.InfoPanelEvent.OnSelectedPackageClick) {
        if (event.eventId is PackageListItem.Package.Id) handle(
            PackageListItemEvent.InfoPanelEvent.OnPackageSelected(event.eventId)
        )
    }

    private fun handle(event: PackageListItemEvent.InfoPanelEvent.OnPackageSelected) {
        val infoPanelViewModel = project.service<InfoPanelViewModel>()
        logPackageSelected(event.eventId is PackageListItem.Package.Declared.Id)
        when (event.eventId) {
            is PackageListItem.Package.Remote.Base.Id -> {
                val headerId = PackageListItem.Header.Id.Remote.Base(event.eventId.moduleIdentity)
                val search = searchResultMapFlow.value[headerId] as? Search.Results.Base
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
                val search = searchResultMapFlow.value[headerId] as? Search.Results.WithVariants
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
            project.PackageSearchProjectService.stableOnlyStateFlow.value ->
                dependency.remoteInfo?.versions?.latestStable?.normalized?.versionName
                    ?: dependency.remoteInfo?.versions?.latest?.normalized?.versionName

            else -> dependency.remoteInfo?.versions?.latest?.normalized?.versionName
        } ?: return
        logPackageVersionChanged(
            packageIdentifier = dependency.id,
            packageFromVersion = dependency.declaredVersion?.versionName,
            packageTargetVersion = newVersion,
            targetModule = module
        )
        editor.editModule {
            manager.updateDependency(dependency, newVersion, dependency.declaredScope)
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
                    logPackageRemoved(
                        packageIdentifier = actionType.eventId.packageId,
                        packageVersion = declaredPackage.declaredVersion?.versionName,
                        targetModule = module
                    )
                    module.removeDependency(declaredPackage)
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
                    logPackageRemoved(
                        packageIdentifier = actionType.eventId.packageId,
                        packageVersion = declaredPackage.declaredVersion?.versionName,
                        targetModule = module
                    )
                    variant.removeDependency(declaredPackage)
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
            .value[actionType.headerId] as? Search.Results.WithVariants
            ?: return
        val apiPackage = search.packages
            .firstOrNull { it.id == actionType.eventId.packageId }
            ?: return
        logPackageInstalled(apiPackage.id, module)
        installDependency(
            manager = variant,
            updater = module,
            apiPackage = apiPackage,
            scope = variant.defaultScope
        )
    }

    private suspend fun handle(actionType: PackageListItemEvent.OnPackageAction.Install.Base) {
        packagesLoadingMutableStateFlow.update { it + actionType.eventId }
        val module = actionType.eventId
            .getModule() as? PackageSearchModule.Base
            ?: return
        val search = searchResultMapFlow.value[actionType.headerId] as? Search.Results ?: return
        val apiPackage = search.packages
            .firstOrNull { it.id == actionType.eventId.packageId }
            ?: return
        logPackageInstalled(apiPackage.id, module)
        installDependency(
            manager = module,
            updater = module,
            apiPackage = apiPackage,
            scope = module.defaultScope
        )
    }

    private suspend fun installDependency(
        manager: PackageSearchDependencyManager,
        updater: PackageSearchModuleEditor,
        apiPackage: ApiPackage,
        scope: String?,
    ) = updater.editModule {
        manager.addDependency(
            apiPackage = apiPackage,
            selectedVersion = when {
                project.PackageSearchProjectService.stableOnlyStateFlow.value ->
                    apiPackage.versions.latestStable?.normalized?.versionName
                        ?: apiPackage.versions.latest.normalized.versionName

                else -> apiPackage.versions.latest.normalized.versionName
            },
            selectedScope = scope
        )
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
        logGoToSource(module, dependency.id)
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
                        viewModelScope.launch {
                            logPackageScopeChanged(dependency.id, dependency.declaredScope, event.scope, module)
                        }
                        manager.updateDependency(
                            declaredPackage = dependency,
                            newVersion = dependency.declaredVersion?.versionName,
                            newScope = event.scope
                        )
                    }

                    is PackageListItemEvent.EditPackageEvent.SetPackageVersion -> {
                        viewModelScope.launch {
                            logPackageVersionChanged(
                                packageIdentifier = dependency.id,
                                packageFromVersion = dependency.declaredVersion?.versionName,
                                packageTargetVersion = event.version,
                                targetModule = module
                            )
                        }
                        manager.updateDependency(
                            declaredPackage = dependency,
                            newVersion = event.version,
                            newScope = dependency.declaredScope
                        )
                    }

                    is PackageListItemEvent.EditPackageEvent.SetVariant -> {
                        viewModelScope.launch {
                            logPackageVariantChanged(dependency.id, module)
                        }
                        logTODO()
                    }
                }
            }
        }
            .onFailure {
                logWarn("Failed to set scope for package:\n${json.encodeToString(event)}", it)
            }
    }

    private suspend fun handle(event: PackageListItemEvent.InfoPanelEvent.OnHeaderAttributesClick) {
        logHeaderAttributesClick(isSearchHeader = event.eventId is PackageListItem.Header.Id.Remote)
        val infoPanelViewModel = project.service<InfoPanelViewModel>()

        val module = event.eventId.getModule() as? PackageSearchModule.WithVariants ?: return

        when (event) {
            is PackageListItemEvent.InfoPanelEvent.OnHeaderAttributesClick.DeclaredHeaderAttributesClick -> {
                val attributes = module.variants[event.variantName]?.attributes ?: return
                infoPanelViewModel.setDeclaredHeaderAttributes(event.variantName, attributes = attributes)
            }

            is PackageListItemEvent.InfoPanelEvent.OnHeaderAttributesClick.SearchHeaderAttributesClick -> {
                val attributes = module
                    .variants
                    .map { it.value.attributes }
                    .flatten()
                    .filter { it.value in event.attributesNames }
                    .distinct()

                val variants = module.variants.values.map { it.name } - module.mainVariantName

                infoPanelViewModel.setSearchHeaderAttributes(
                    defaultVariant = module.mainVariantName,
                    additionalVariants = variants,
                    attributes = attributes
                )
            }
        }


        project.service<ToolWindowViewModel>().isInfoPanelOpen.let { openStateFlow ->
            if (!openStateFlow.value) {
                openStateFlow.emit(true)
            }
        }

    }


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
            variant.removeDependency(declaredPackage)
//            newVariant.addDependency(
//                apiPackage = declaredPackage,
//                selectedVersion = declaredPackage.declaredVersion?.versionName,
//                selectedScope = declaredPackage.declaredScope
//            )
        }
    }

    private suspend fun handle(event: PackageListItemEvent.UpdateAllPackages) {
        headerLoadingStatesFlow.update { it + event.eventId }
        logUpgradeAll()
        val onlyStable = project.PackageSearchProjectService.stableOnlyStateFlow.value
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
                        packagesToUpdate.forEach {
                            module.updateDependency(it, it.getLatestVersion(onlyStable)?.versionName, it.declaredScope)
                        }
                    }
                }
                    .onFailure {
                        logWarn("Failed to update packages:\n${json.encodeToString(event)}", it)
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
                                    declaredPackage = declaredPackage,
                                    newVersion = declaredPackage.getLatestVersion(onlyStable)?.versionName,
                                    newScope = declaredPackage.declaredScope
                                )
                            }
                    }
                }
                    .onFailure {
                        logWarn("Failed to update packages:\n${json.encodeToString(event)}", it)
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
