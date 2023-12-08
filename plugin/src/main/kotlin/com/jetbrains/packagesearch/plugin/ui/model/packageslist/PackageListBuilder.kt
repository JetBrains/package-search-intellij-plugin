package com.jetbrains.packagesearch.plugin.ui.model.packageslist

import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.ui.model.getLatestVersion
import com.jetbrains.packagesearch.plugin.ui.model.hasUpdates
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent.SetHeaderState.TargetState
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage

class PackageListBuilder(
    private val isCompact: Boolean,
    private val onlyStable: Boolean,
    private val headerCollapsedStates: Map<PackageListItem.Header.Id, TargetState>,
    private val packagesLoadingState: Set<PackageListItem.Package.Id>,
    private val headerLoadingStates: Set<PackageListItem.Header.Id.Declared>,
    private val searchQuery: String,
    private val modulesMap: Map<PackageSearchModule.Identity, PackageSearchModule>,
) {

    companion object {
        const val MAX_SEARCH_RESULTS = 25
    }

    private val items = mutableListOf<PackageListItem>()

    fun build(): List<PackageListItem> = items.toList()

    private fun getStateForOrOpen(id: PackageListItem.Header.Id) =
        when (headerCollapsedStates[id]) {
            TargetState.CLOSE -> PackageListItem.Header.State.CLOSED
            else -> PackageListItem.Header.State.OPEN
        }

    private fun List<PackageSearchDeclaredPackage>.getUpdatesAvailableAdditionalContent() =
        filter { it.matchesSearchQuery() }
            .count { it.hasUpdates(onlyStable) }
            .takeIf { it > 0 }
            ?.let { PackageListItem.Header.AdditionalContent.UpdatesAvailableCount(it) }

    private fun PackageSearchDeclaredPackage.getAvailableVersionStrings() = when {
        onlyStable -> remoteInfo?.versions?.all
            ?.asSequence()
            ?.filter { it.normalizedVersion.isStable }
            ?.map { it.normalizedVersion }
            ?.sortedDescending()
            ?.map { it.versionName }
            ?.toList()
            ?: emptyList()

        else -> remoteInfo?.versions?.all
            ?.asSequence()
            ?.map { it.normalizedVersion }
            ?.sortedDescending()
            ?.map { it.versionName }
            ?.toList()
            ?: emptyList()
    }

    private fun PackageSearchDeclaredPackage.matchesSearchQuery() = matchesSearchQuery(searchQuery)

    fun addFromModules(modules: List<PackageSearchModule>) {
        modules.forEach { addFromModule(it) }
    }

    fun addFromModule(module: PackageSearchModule) {
        when (module) {
            is PackageSearchModule.Base -> addFromBaseModule(module)
            is PackageSearchModule.WithVariants -> addFromModuleWithVariants(module)
        }
    }

    fun addFromBaseModule(base: PackageSearchModule.Base) {
        val id = PackageListItem.Header.Id.Declared.Base(base.identity)
        val dependenciesToShow = base.declaredDependencies
            .filter { it.matchesSearchQuery() }
        val state = getStateForOrOpen(id)
        if (dependenciesToShow.isNotEmpty() || isCompact) {
            addHeader(
                title = base.name,
                id = id,
                state = state,
                additionalContent = when (id) {
                    in headerLoadingStates -> PackageListItem.Header.AdditionalContent.Loading
                    else -> dependenciesToShow.getUpdatesAvailableAdditionalContent()
                }
            )
        }
        if (state == PackageListItem.Header.State.OPEN) {
            dependenciesToShow
                .filter { it.matchesSearchQuery() }
                .forEach { dependency ->
                addDeclaredPackage(
                    title = dependency.displayName,
                    subtitle = dependency.coordinates,
                    id = PackageListItem.Package.Declared.Id.Base(base.identity, dependency.id),
                    icon = dependency.icon,
                    latestVersion = dependency.getLatestVersion(onlyStable)?.versionName,
                    selectedScope = dependency.declaredScope,
                    availableScopes = base.availableScopes,
                    declaredVersion = dependency.declaredVersion?.versionName,
                    availableVersions = dependency.getAvailableVersionStrings(),
                    allowMissingScope = !base.dependencyMustHaveAScope,
                )
            }
        }
    }

    fun addDeclaredPackage(
        title: String,
        subtitle: String,
        id: PackageListItem.Package.Declared.Id,
        icon: IconProvider.Icon,
        latestVersion: String? = null,
        selectedScope: String?,
        availableScopes: List<String>,
        declaredVersion: String?,
        availableVersions: List<String>,
        allowMissingScope: Boolean,
    ) {
        items.add(
            PackageListItem.Package.Declared(
                title = title,
                id = id,
                subtitle = subtitle,
                icon = icon,
                isLoading = id in packagesLoadingState,
                latestVersion = latestVersion,
                selectedScope = selectedScope,
                availableScopes = availableScopes.filter { it != selectedScope },
                declaredVersion = declaredVersion,
                availableVersions = availableVersions.filter { it != declaredVersion },
                allowMissingScope = allowMissingScope,
            )
        )
    }

    fun addHeader(
        title: String,
        id: PackageListItem.Header.Id,
        state: PackageListItem.Header.State,
        attributes: List<String> = emptyList(),
        additionalContent: PackageListItem.Header.AdditionalContent? = null,
    ) {
        items.add(
            PackageListItem.Header(
                title = title,
                id = id,
                state = state,
                attributes = attributes,
                additionalContent = additionalContent,
            )
        )
    }

    fun addFromModuleWithVariants(withVariants: PackageSearchModule.WithVariants) {
        if (isCompact) {
            addFromModuleWithVariantsCompact(withVariants)
        } else {
            addFromModuleWithVariantsExpanded(withVariants)
        }
    }

    private fun addFromModuleWithVariantsExpanded(module: PackageSearchModule.WithVariants) {
        module.variants
            .values
            .forEach { variant ->
                val id = PackageListItem.Header.Id.Declared.WithVariant(
                    moduleIdentity = module.identity,
                    variantName = variant.name
                )
                val dependenciesToShow = variant.declaredDependencies
                    .filter { it.matchesSearchQuery() }
                if (dependenciesToShow.isEmpty()) return@forEach
                val state = getStateForOrOpen(id)
                addHeader(
                    title = variant.name,
                    id = PackageListItem.Header.Id.Declared.WithVariant(
                        moduleIdentity = module.identity,
                        variantName = variant.name
                    ),
                    state = state,
                    attributes = variant.attributes.map { it.value },
                    additionalContent = when (id) {
                        in headerLoadingStates -> PackageListItem.Header.AdditionalContent.Loading
                        else -> variant.declaredDependencies.getUpdatesAvailableAdditionalContent()
                    }
                )
                if (state == PackageListItem.Header.State.OPEN) {
                    variant.declaredDependencies
                        .filter { it.matchesSearchQuery() }
                        .forEach { dependency ->
                        addDeclaredPackage(
                            title = dependency.displayName,
                            subtitle = dependency.coordinates,
                            id = PackageListItem.Package.Declared.Id.WithVariant(
                                moduleIdentity = module.identity,
                                packageId = dependency.id,
                                variantName = variant.name,
                            ),
                            icon = dependency.icon,
                            latestVersion = dependency.getLatestVersion(onlyStable)?.versionName,
                            selectedScope = dependency.declaredScope,
                            availableScopes = variant.availableScopes,
                            declaredVersion = dependency.declaredVersion?.versionName,
                            availableVersions = dependency.getAvailableVersionStrings(),
                            allowMissingScope = !module.dependencyMustHaveAScope,
                        )
                    }
                }
            }
    }

    private fun addFromModuleWithVariantsCompact(module: PackageSearchModule.WithVariants) {
        val id = PackageListItem.Header.Id.Declared.Base(module.identity)
        val dependenciesToShow = module.variants
            .values
            .flatMap { variant -> variant.declaredDependencies.map { variant to it } }
            .filter { it.second.matchesSearchQuery() }
        val state = getStateForOrOpen(id)
        addHeader(
            title = module.name,
            id = id,
            state = state,
            additionalContent = when (id) {
                in headerLoadingStates -> PackageListItem.Header.AdditionalContent.Loading
                else -> dependenciesToShow.map { it.second }.getUpdatesAvailableAdditionalContent()

            }
        )
        if (state == PackageListItem.Header.State.OPEN) {
            dependenciesToShow
                .filter { it.second.matchesSearchQuery() }
                .forEach { (variant, dependency) ->
                addDeclaredPackage(
                    title = dependency.displayName,
                    subtitle = variant.name,
                    id = PackageListItem.Package.Declared.Id.WithVariant(
                        module.identity,
                        dependency.id,
                        variant.name,
                    ),
                    icon = dependency.icon,
                    latestVersion = dependency.getLatestVersion(onlyStable)?.versionName,
                    selectedScope = dependency.declaredScope,
                    availableScopes = variant.availableScopes,
                    declaredVersion = dependency.declaredVersion?.versionName,
                    availableVersions = dependency.getAvailableVersionStrings(),
                    allowMissingScope = !module.dependencyMustHaveAScope,
                )
            }
        }
    }

    fun addFromSearch(searchResultMap: Map<PackageListItem.Header.Id.Remote, Search>) {
        searchResultMap.forEach { (headerId, search) ->
            when (search) {
                is Search.Query.Base -> addHeader(
                    title = PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.searchResults"),
                    id = headerId,
                    state = when (headerCollapsedStates[headerId]) {
                        TargetState.OPEN -> PackageListItem.Header.State.LOADING
                        else -> PackageListItem.Header.State.CLOSED
                    }
                )

                is Search.Query.WithVariants -> addHeader(
                    title = PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.searchResults"),
                    id = headerId,
                    state = when (headerCollapsedStates[headerId]) {
                        TargetState.OPEN -> PackageListItem.Header.State.LOADING
                        else -> PackageListItem.Header.State.CLOSED
                    },
                    attributes = search.attributes,
                    additionalContent = search.buildVariantsText()
                )

                is Search.Results.Base -> addFromSearchQueryBase(
                    headerId = headerId as PackageListItem.Header.Id.Remote.Base,
                    search = search,
                    module = modulesMap[headerId.moduleIdentity] as? PackageSearchModule.Base ?: return@forEach
                )

                is Search.Results.WithVariants -> addFromSearchQueryWithVariants(
                    headerId = headerId as PackageListItem.Header.Id.Remote.WithVariant,
                    search = search,
                    module = modulesMap[headerId.moduleIdentity] as? PackageSearchModule.WithVariants ?: return@forEach
                )
            }
        }
    }

    private fun addFromSearchQueryWithVariants(
        headerId: PackageListItem.Header.Id.Remote.WithVariant,
        search: Search.Results.WithVariants,
        module: PackageSearchModule.WithVariants,
    ) {
        val state = when (headerCollapsedStates[headerId]) {
            TargetState.OPEN -> PackageListItem.Header.State.OPEN
            else -> PackageListItem.Header.State.CLOSED
        }
        addHeader(
            title = PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.searchResults"),
            id = headerId,
            state = state,
            attributes = search.attributes,
            additionalContent = search.buildVariantsText(),
        )
        if (state == PackageListItem.Header.State.OPEN) {
            val declaredPackageIds = module.variants
                .values
                .firstOrNull { it.isPrimary && it.name == search.primaryVariantName }
                ?.declaredDependencies
                ?.map { it.id }
                ?: emptyList()
            search.packages
                .take(MAX_SEARCH_RESULTS)
                .forEach { apiPackage ->
                    val additionalVariants = search.additionalVariants.filter {
                        module.variants[it]
                            ?.declaredDependencies
                            ?.none { it.id == apiPackage.id } == true
                    }
                    val isInstalledInPrimaryVariant = apiPackage.id in declaredPackageIds
                    if (isInstalledInPrimaryVariant && additionalVariants.isEmpty()) return@forEach
                    addRemotePackageWithVariants(
                        title = apiPackage.name,
                        subtitle = apiPackage.coordinates,
                        id = PackageListItem.Package.Remote.WithVariant.Id(
                            moduleIdentity = headerId.moduleIdentity,
                            packageId = apiPackage.id,
                            headerId = headerId,
                        ),
                        primaryVariant = search.primaryVariantName,
                        icon = when (apiPackage) {
                            is ApiMavenPackage -> IconProvider.Icons.MAVEN
                        },
                        additionalVariants = additionalVariants,
                        isInstalledInPrimaryVariant = isInstalledInPrimaryVariant
                    )
                }
        }
    }

    private fun addFromSearchQueryBase(
        headerId: PackageListItem.Header.Id.Remote.Base,
        search: Search.Results.Base,
        module: PackageSearchModule.Base,
    ) {
        val state = when (headerCollapsedStates[headerId]) {
            TargetState.OPEN -> PackageListItem.Header.State.OPEN
            else -> PackageListItem.Header.State.CLOSED
        }
        addHeader(
            title = PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.searchResults"),
            id = headerId,
            state = state
        )
        if (state == PackageListItem.Header.State.OPEN) {
            val declaredPackageIds = module.declaredDependencies
                .map { it.id }
            search.packages
                .take(MAX_SEARCH_RESULTS)
                .filter { it.id !in declaredPackageIds }
                .forEach { apiPackage ->
                    addRemoteBasePackage(
                        title = apiPackage.name,
                        subtitle = apiPackage.coordinates,
                        id = PackageListItem.Package.Remote.Base.Id(
                            moduleIdentity = headerId.moduleIdentity,
                            packageId = apiPackage.id,
                            headerId = headerId,
                        ),
                        icon = when (apiPackage) {
                            is ApiMavenPackage -> IconProvider.Icons.MAVEN
                        }
                    )
                }
        }
    }


    private fun addRemotePackageWithVariants(
        title: String,
        subtitle: String,
        id: PackageListItem.Package.Remote.WithVariant.Id,
        primaryVariant: String,
        icon: IconProvider.Icon,
        additionalVariants: List<String>,
        isInstalledInPrimaryVariant: Boolean,
    ) {
        items.add(
            PackageListItem.Package.Remote.WithVariant(
                title = title,
                id = id,
                subtitle = subtitle,
                icon = icon,
                isLoading = id in packagesLoadingState,
                primaryVariantName = primaryVariant,
                additionalVariants = additionalVariants,
                isInstalledInPrimaryVariant = isInstalledInPrimaryVariant,
            )
        )
    }

    private fun addRemoteBasePackage(
        title: String,
        subtitle: String,
        id: PackageListItem.Package.Remote.Base.Id,
        icon: IconProvider.Icon,
    ) {
        items.add(
            PackageListItem.Package.Remote.Base(
                title = title,
                id = id,
                subtitle = subtitle,
                icon = icon,
                isLoading = id in packagesLoadingState,
            )
        )
    }


}
