package com.jetbrains.packagesearch.plugin.ui.model.infopanel

import androidx.compose.foundation.ScrollState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItem
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListViewModel
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchSettingsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.packagesearch.api.v3.ApiPackage

@Service(Level.PROJECT)
class InfoPanelViewModel(
    private val project: Project,
    viewModelScope: CoroutineScope,
) {

    private val setDataEventChannel = Channel<InfoPanelContentEvent>()

    val scrollState = ScrollState(0)

    private val packageLoadingState
        get() = project.service<PackageListViewModel>().packagesLoadingStateFlow

    val tabs: StateFlow<List<InfoPanelContent>> = combine(
        setDataEventChannel.consumeAsFlow(),
        project.PackageSearchSettingsService.stableOnlyFlow,
        packageLoadingState
    ) { event, onlyStable, packageLoadingState ->
        when (event) {
            is InfoPanelContentEvent.Package -> {
                val isLoading = event.packageListId in packageLoadingState
                when (event) {
                    is InfoPanelContentEvent.Package.Declared.Base ->
                        event.asPanelContent(project.PackageSearchProjectService, onlyStable, isLoading)

                    is InfoPanelContentEvent.Package.Declared.WithVariant ->
                        event.asPanelContent(project.PackageSearchProjectService, onlyStable, isLoading)

                    is InfoPanelContentEvent.Package.Remote.Base ->
                        event.asPanelContent(project.PackageSearchProjectService, onlyStable, isLoading)

                    is InfoPanelContentEvent.Package.Remote.WithVariants ->
                        event.asPanelContent(project.PackageSearchProjectService, onlyStable, isLoading)
                }
            }

            is InfoPanelContentEvent.Attributes.FromVariant -> {
                event.asPanelContent()
            }

            is InfoPanelContentEvent.Attributes.FromSearch -> {
                event.asPanelContent()
            }
        }
    }
        .retry(5)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val activeTabTitleMutableStateFlow: MutableStateFlow<String?> = MutableStateFlow(null)
    val activeTabTitleFlow = activeTabTitleMutableStateFlow.asStateFlow()

    fun setActiveTabTitle(title: String) {
        activeTabTitleMutableStateFlow.value = title
    }

    init {
        combine(
            tabs.map { it.map { it.tabTitleData } },
            activeTabTitleMutableStateFlow
        ) { tabTitles, activeTabTitle ->
            if (activeTabTitle !in tabTitles.map { it.tabTitle }) tabTitles.firstOrNull()?.tabTitle else activeTabTitle
        }
            .mapNotNull { it }
            .onEach { activeTabTitleMutableStateFlow.emit(it) }
            .launchIn(viewModelScope)
    }

    fun setPackage(
        module: PackageSearchModule.Base,
        declaredPackage: PackageSearchDeclaredPackage,
        packageId: PackageListItem.Package.Declared.Id.Base,
    ) {
        setDataEventChannel.trySend(InfoPanelContentEvent.Package.Declared.Base(module, declaredPackage, packageId))
    }

    fun setPackage(
        module: PackageSearchModule.WithVariants,
        declaredPackage: PackageSearchDeclaredPackage,
        packageId: PackageListItem.Package.Declared.Id.WithVariant,
        variantName: String,
    ) {
        setDataEventChannel.trySend(
            InfoPanelContentEvent.Package.Declared.WithVariant(
                module = module,
                declaredPackage = declaredPackage,
                packageListId = packageId,
                variantName = variantName
            )
        )
    }

    fun setPackage(
        module: PackageSearchModule.Base,
        apiPackage: ApiPackage,
        packageId: PackageListItem.Package.Remote.Base.Id,
    ) {
        setDataEventChannel.trySend(InfoPanelContentEvent.Package.Remote.Base(module, apiPackage, packageId))
    }

    fun setPackage(
        module: PackageSearchModule.WithVariants,
        apiPackage: ApiPackage,
        primaryVariantName: String,
        packageId: PackageListItem.Package.Remote.WithVariant.Id,
    ) {
        setDataEventChannel.trySend(
            InfoPanelContentEvent.Package.Remote.WithVariants(
                module = module,
                apiPackage = apiPackage,
                primaryVariantName = primaryVariantName,
                compatibleVariantNames = packageId.headerId.compatibleVariantNames,
                packageListId = packageId
            )
        )
    }

    fun setDeclaredHeaderAttributes(
        variantName: String,
        attributes: List<PackageSearchModuleVariant.Attribute>,
    ) {
        setDataEventChannel.trySend(
            InfoPanelContentEvent.Attributes.FromVariant(
                variantName = variantName,
                attributes = attributes
            )
        )
    }

    fun setSearchHeaderAttributes(
        defaultVariant: String,
        additionalVariants: List<String>,
        attributes: List<PackageSearchModuleVariant.Attribute>,
    ) {
        setDataEventChannel.trySend(
            InfoPanelContentEvent.Attributes.FromSearch(
                defaultVariant = defaultVariant,
                additionalVariants = additionalVariants,
                attributes = attributes
            )
        )
    }

}



