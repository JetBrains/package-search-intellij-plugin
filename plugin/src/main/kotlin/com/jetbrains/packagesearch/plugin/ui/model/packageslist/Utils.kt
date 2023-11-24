package com.jetbrains.packagesearch.plugin.ui.model.packageslist

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleEditor
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.utils.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.runningFold
import org.jetbrains.packagesearch.api.v3.search.PackagesType

internal fun <T, R> Flow<T>.modifiedBy(
    transform: Flow<R>,
    function: suspend (T, R) -> T,
): Flow<T> = flatMapLatest {
    transform.runningFold(it) { acc, value -> function(acc, value) }
}

internal fun Map<String, PackageSearchModuleVariant>.groupByCompatiblePackageTypes(): Map<List<PackagesType>, List<PackageSearchModuleVariant>> =
    values.groupBy { it.compatiblePackageTypes }

internal fun Search.WithVariants.buildVariantsText() =
    PackageListItem.Header.AdditionalContent.VariantsText(buildString {
        append(primaryVariantName)
        additionalVariants.firstOrNull()?.let { append(", $it") }
        if (additionalVariants.size > 1) {
            append(", +${additionalVariants.size - 1}")
        }
    })

internal val Search.WithVariants.compatibleVariants
    get() = buildList {
        add(primaryVariantName)
        addAll(additionalVariants)
    }

internal fun buildPackageList(
    isCompact: Boolean,
    onlyStable: Boolean,
    headerCollapsedStates: Map<PackageListItem.Header.Id, PackageListItemEvent.SetHeaderState.TargetState>,
    packagesLoadingState: Set<PackageListItem.Package.Id>,
    searchQuery: String,
    headerLoadingStates: Set<PackageListItem.Header.Id.Declared>,
    modulesMap: Map<PackageSearchModule.Identity, PackageSearchModule>,
    block: PackageListBuilder.() -> Unit,
): List<PackageListItem> = PackageListBuilder(
    isCompact = isCompact,
    onlyStable = onlyStable,
    headerCollapsedStates = headerCollapsedStates,
    packagesLoadingState = packagesLoadingState,
    headerLoadingStates = headerLoadingStates,
    searchQuery = searchQuery,
    modulesMap = modulesMap
).apply(block).build()

internal data class PackageSearchDependencyHandlers(
    val modifier: PackageSearchModuleEditor,
    val manager: PackageSearchDependencyManager,
    val declaredPackage: PackageSearchDeclaredPackage,
)

internal fun PackageSearchDependencyHandlers(
    module: PackageSearchModule.Base,
    declaredPackage: PackageSearchDeclaredPackage,
) =
    PackageSearchDependencyHandlers(module, module, declaredPackage)

internal fun combineListChanges(
    modules: Flow<List<PackageSearchModule>>,
    searchResultMap: Flow<Map<PackageListItem.Header.Id.Remote, Search>>,
    headerCollapsedStates: Flow<Map<PackageListItem.Header.Id, PackageListItemEvent.SetHeaderState.TargetState>>,
    packagesLoadingState: Flow<Set<PackageListItem.Package.Id>>,
    searchQuery: Flow<String>,
    stableOnly: Flow<Boolean>,
    headerLoadingStates: MutableStateFlow<Set<PackageListItem.Header.Id.Declared>>,
): Flow<PackageListChange> = combine(
    modules,
    searchResultMap,
    headerCollapsedStates,
    packagesLoadingState,
    searchQuery,
    stableOnly,
    headerLoadingStates
) {
        modules,
        searchResultMap,
        headersState,
        packagesLoadingState,
        searchQuery,
        stableOnly,
        headerLoadingStates,
    ->
    PackageListChange(
        selectedModules = modules,
        searchResultMap = searchResultMap,
        headerCollapsedStates = headersState,
        packagesLoadingState = packagesLoadingState,
        searchQuery = searchQuery,
        stableOnly = stableOnly,
        headerLoadingStates = headerLoadingStates,
    )
}

data class PackageListChange(
    val selectedModules: List<PackageSearchModule>,
    val searchResultMap: Map<PackageListItem.Header.Id.Remote, Search>,
    val headerCollapsedStates: Map<PackageListItem.Header.Id, PackageListItemEvent.SetHeaderState.TargetState>,
    val packagesLoadingState: Set<PackageListItem.Package.Id>,
    val searchQuery: String,
    val stableOnly: Boolean,
    val headerLoadingStates: Set<PackageListItem.Header.Id.Declared>,
)