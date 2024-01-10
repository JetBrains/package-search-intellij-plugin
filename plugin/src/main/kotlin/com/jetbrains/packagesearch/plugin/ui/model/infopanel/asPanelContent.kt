package com.jetbrains.packagesearch.plugin.ui.model.infopanel

import com.jetbrains.packagesearch.plugin.PackageSearchBundle.message
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchKnownRepositoriesContext
import com.jetbrains.packagesearch.plugin.core.utils.icon
import com.jetbrains.packagesearch.plugin.ui.model.getLatestVersion
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage

context(PackageSearchKnownRepositoriesContext)
fun ApiPackage.repositories() =
    versions.latest
        .repositoryIds
        .mapNotNull { knownRepositories[it] }
        .map { InfoPanelContent.PackageInfo.Repository(it.name, it.url) }

context(PackageSearchKnownRepositoriesContext)
internal fun InfoPanelContentEvent.Package.Declared.Base.asPanelContent(
    onlyStable: Boolean,
    isLoading: Boolean,
) = listOf(
    InfoPanelContent.PackageInfo.Declared.Base(
        moduleId = module.identity,
        packageListId = packageListId,
        tabTitle = message("packagesearch.ui.toolwindow.packages.details.info.overview"),
        title = declaredPackage.displayName,
        subtitle = declaredPackage.coordinates,
        icon = declaredPackage.icon,
        type = declaredPackage.typeInfo,
        licenses = declaredPackage.remoteInfo?.licenses?.asInfoPanelLicenseList() ?: emptyList(),
        authors = declaredPackage.remoteInfo?.authors?.mapNotNull { it.name } ?: emptyList(),
        description = declaredPackage.remoteInfo
            ?.description
            ?.sanitizeDescription(),
        scm = declaredPackage.remoteInfo?.scm?.asInfoPanelScm(),
        readmeUrl = declaredPackage.remoteInfo?.scm?.readme?.htmlUrl ?: declaredPackage.remoteInfo?.scm?.readmeUrl,
        repositories = declaredPackage.remoteInfo?.repositories() ?: emptyList(),
        latestVersion = declaredPackage.getLatestVersion(onlyStable)?.versionName,
        declaredVersion = declaredPackage.declaredVersion
            ?.versionName
            ?: message("packagesearch.ui.missingVersion"),
        declaredScope = declaredPackage.declaredScope
            ?: message("packagesearch.ui.missingScope"),
        availableVersions = declaredPackage.remoteInfo
            ?.versions
            ?.all
            ?.filter { if (onlyStable) it.normalizedVersion.isStable else true }
            ?.map { it.normalizedVersion.versionName }
            ?: emptyList(),
        availableScopes = module.availableScopes,
        isLoading = isLoading,
        allowMissingScope = !module.dependencyMustHaveAScope
    )
)

internal val PackageSearchDeclaredPackage.typeInfo: InfoPanelContent.PackageInfo.Type?
    get() {
        return InfoPanelContent.PackageInfo.Type(
            name = when (remoteInfo) {
                is ApiMavenPackage -> message("packagesearch.configuration.maven.title")
                null -> when (icon) {
                    IconProvider.Icons.MAVEN -> message("packagesearch.configuration.maven.title")
                    IconProvider.Icons.NPM -> message("packagesearch.configuration.npm.title")
                    IconProvider.Icons.COCOAPODS -> message("packagesearch.configuration.cocoapods.title")
                    else -> return null
                }
            },
            icon = icon
        )
    }

internal val ApiPackage.typeInfo: InfoPanelContent.PackageInfo.Type
    get() = InfoPanelContent.PackageInfo.Type(
        name = when (this) {
            is ApiMavenPackage -> message("packagesearch.configuration.maven.title")
        },
        icon = icon
    )


private fun String.sanitizeDescription() =
    replace("\r\n", "\n")
        .replace("\r", "\n")
        .split("\n")
        .joinToString("\n") { it.trimStart() }


context(PackageSearchKnownRepositoriesContext)
internal fun InfoPanelContentEvent.Package.Declared.WithVariant.asPanelContent(
    onlyStable: Boolean,
    isLoading: Boolean,
) = listOf(
    InfoPanelContent.PackageInfo.Declared.WithVariant(
        moduleId = module.identity,
        packageListId = packageListId,
        tabTitle = message("packagesearch.ui.toolwindow.packages.details.info.overview"),
        title = declaredPackage.displayName,
        subtitle = declaredPackage.coordinates,
        icon = declaredPackage.icon,
        type = declaredPackage.typeInfo,
        licenses = declaredPackage.remoteInfo?.licenses?.asInfoPanelLicenseList() ?: emptyList(),
        authors = declaredPackage.remoteInfo?.authors?.mapNotNull { it.name } ?: emptyList(),
        description = declaredPackage.remoteInfo
            ?.description
            ?.sanitizeDescription(),
        scm = declaredPackage.remoteInfo?.scm?.asInfoPanelScm(),
        readmeUrl = declaredPackage.remoteInfo?.scm?.readme?.htmlUrl ?: declaredPackage.remoteInfo?.scm?.readmeUrl,
        repositories = declaredPackage.remoteInfo?.repositories() ?: emptyList(),
        latestVersion = declaredPackage.getLatestVersion(onlyStable)?.versionName,
        declaredVersion = declaredPackage.declaredVersion
            ?.versionName
            ?: message("packagesearch.ui.missingVersion"),
        declaredScope = declaredPackage.declaredScope
            ?: message("packagesearch.ui.missingScope"),
        availableVersions = declaredPackage.remoteInfo
            ?.versions
            ?.all
            ?.filter { if (onlyStable) it.normalizedVersion.isStable else true }
            ?.map { it.normalizedVersion.versionName }
            ?: emptyList(),
        availableScopes = module.variants.getValue(variantName).availableScopes,
        isLoading = isLoading,
        compatibleVariants = module.variants.keys.sorted() - variantName,
        declaredVariant = variantName,
        allowMissingScope = !module.dependencyMustHaveAScope,
        variantTerminology = module.variantTerminology
    ),
    InfoPanelContent.Attributes.FromVariant(
        variantName = variantName,
        tabTitle = message("packagesearch.ui.toolwindow.sidepanel.platforms"),
        attributes = module.variants.getValue(variantName).attributes
    )
)

context(PackageSearchKnownRepositoriesContext)
internal fun InfoPanelContentEvent.Package.Remote.WithVariants.asPanelContent(
    isLoading: Boolean,
) = listOf(
    InfoPanelContent.PackageInfo.Remote.WithVariant(
        tabTitle = message("packagesearch.ui.toolwindow.packages.details.info.overview"),
        moduleId = module.identity,
        packageListId = packageListId,
        title = apiPackage.name,
        subtitle = apiPackage.coordinates,
        icon = apiPackage.icon,
        type = apiPackage.typeInfo,
        licenses = apiPackage.licenses?.asInfoPanelLicenseList() ?: emptyList(),
        authors = apiPackage.authors.mapNotNull { it.name },
        description = apiPackage.description?.sanitizeDescription(),
        scm = apiPackage.scm?.asInfoPanelScm(),
        readmeUrl = apiPackage.scm?.readme?.htmlUrl ?: apiPackage.scm?.readmeUrl,
        primaryVariant = primaryVariantName,
        additionalVariants = compatibleVariantNames.sorted() - primaryVariantName,
        repositories = apiPackage.repositories(),
        isLoading = isLoading,
        isInstalledInPrimaryVariant = module.variants.getValue(primaryVariantName).declaredDependencies
            .any { it.id == apiPackage.id }
    ),
    InfoPanelContent.Attributes.FromVariant(
        tabTitle = message("packagesearch.ui.toolwindow.sidepanel.platforms"),
        variantName = primaryVariantName,
        attributes = module.variants.getValue(primaryVariantName).attributes
    )
)

context(PackageSearchKnownRepositoriesContext)
internal fun InfoPanelContentEvent.Package.Remote.Base.asPanelContent(
    isLoading: Boolean,
) = listOf(
    InfoPanelContent.PackageInfo.Remote.Base(
        tabTitle = message("packagesearch.ui.toolwindow.packages.details.info.overview"),
        moduleId = module.identity,
        packageListId = packageListId,
        title = apiPackage.name,
        subtitle = apiPackage.coordinates,
        icon = apiPackage.icon,
        type = apiPackage.typeInfo,
        licenses = apiPackage.licenses?.asInfoPanelLicenseList() ?: emptyList(),
        authors = apiPackage.authors.mapNotNull { it.name },
        description = apiPackage.description?.sanitizeDescription(),
        scm = apiPackage.scm?.asInfoPanelScm(),
        readmeUrl = apiPackage.scm?.readme?.htmlUrl ?: apiPackage.scm?.readmeUrl,
        repositories = apiPackage.repositories(),
        isLoading = isLoading
    )
)

internal fun InfoPanelContentEvent.Attributes.FromVariant.asPanelContent() = listOf(
    InfoPanelContent.Attributes.FromVariant(
        variantName = variantName,
        tabTitle = message("packagesearch.ui.toolwindow.sidepanel.platforms"),
        attributes = attributes,
    )
)

internal fun InfoPanelContentEvent.Attributes.FromSearch.asPanelContent() = listOf(
    InfoPanelContent.Attributes.FromSearch(
        tabTitle = message("packagesearch.ui.toolwindow.packages.details.info.attributes"),
        defaultSourceSet = defaultVariant,
        additionalSourceSets = additionalVariants,
        attributes = attributes,
    )
)