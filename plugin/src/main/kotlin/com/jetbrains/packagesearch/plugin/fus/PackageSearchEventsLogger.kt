/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BaseEventId
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.validator.rules.impl.LocalFileCustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.utils.PackageSearchLogger
import org.jetbrains.idea.reposearch.statistics.TopPackageIdValidationRule
import org.jetbrains.packagesearch.api.v3.ApiRepository

private const val FUS_ENABLED = true

internal class PackageSearchEventsLogger : CounterUsagesCollector() {
    override fun getGroup() = GROUP
}

private const val VERSION = 13

private val GROUP = EventLogGroup(FUSGroupIds.GROUP_ID, VERSION)

internal class TopScopesValidationRule : LocalFileCustomValidationRule(
    /* ruleId = */ "top_scopes_id",
    /* resource = */ TopScopesValidationRule::class.java,
    /* path = */ "/fus/scopes.txt"
)

// FIELDS
private val buildSystemField = EventFields.Class(FUSGroupIds.MODULE_OPERATION_PROVIDER_CLASS)
private val packageIdField =
    EventFields.StringValidatedByCustomRule(FUSGroupIds.PACKAGE_ID, TopPackageIdValidationRule::class.java)
private val packageVersionField =
    EventFields.StringValidatedByRegexpReference(FUSGroupIds.PACKAGE_VERSION, regexpRef = "version")
private val packageFromVersionField =
    EventFields.StringValidatedByRegexpReference(FUSGroupIds.PACKAGE_FROM_VERSION, regexpRef = "version")
private val packageScopeFromField =
    EventFields.StringValidatedByCustomRule<TopScopesValidationRule>(FUSGroupIds.PACKAGE_FROM_SCOPE)
private val packageScopeToField =
    EventFields.StringValidatedByCustomRule<TopScopesValidationRule>(FUSGroupIds.PACKAGE_TO_SCOPE)
private val repositoryIdField = EventFields.Enum<FUSGroupIds.IndexedRepositories>(FUSGroupIds.REPOSITORY_ID)
private val repositoryUrlField =
    EventFields.String(FUSGroupIds.REPOSITORY_URL, allowedValues = FUSGroupIds.indexedRepositoryUrls)
private val repositoryUsesCustomUrlField = EventFields.Boolean(FUSGroupIds.REPOSITORY_USES_CUSTOM_URL)
private val packageIsInstalledField = EventFields.Boolean(FUSGroupIds.PACKAGE_IS_INSTALLED)
private val targetModulesCountField = EventFields.Int(FUSGroupIds.TARGET_MODULES)
private val targetModulesMixedBuildSystemsField =
    EventFields.Boolean(FUSGroupIds.TARGET_MODULES_MIXED_BUILD_SYSTEMS)
private val preferencesGradleScopeCountField = EventFields.Int(FUSGroupIds.PREFERENCES_GRADLE_SCOPES_COUNT)
private val preferencesUpdateScopesOnUsageField = EventFields.Boolean(FUSGroupIds.PREFERENCES_UPDATE_SCOPES_ON_USAGE)
private val preferencesDefaultGradleScopeChangedField =
    EventFields.Boolean(FUSGroupIds.PREFERENCES_DEFAULT_GRADLE_SCOPE_CHANGED)
private val preferencesDefaultMavenScopeChangedField =
    EventFields.Boolean(FUSGroupIds.PREFERENCES_DEFAULT_MAVEN_SCOPE_CHANGED)
private val preferencesAutoAddRepositoriesField =
    EventFields.Boolean(FUSGroupIds.PREFERENCES_AUTO_ADD_REPOSITORIES)
private val detailsLinkLabelField =
    EventFields.Enum<FUSGroupIds.DetailsLinkTypes>(FUSGroupIds.DETAILS_LINK_LABEL)
private val toggleValueField = EventFields.Boolean(FUSGroupIds.CHECKBOX_STATE)
private val searchQueryLengthField = EventFields.Int(FUSGroupIds.SEARCH_QUERY_LENGTH)
private val isSearchHeader = EventFields.Boolean(FUSGroupIds.IS_SEARCH_HEADER)

// EVENTS
private val packageInstalledEvent = GROUP.registerEvent(
    eventId = FUSGroupIds.PACKAGE_INSTALLED,
    eventField1 = packageIdField,
    eventField2 = buildSystemField
)
private val packageRemovedEvent = GROUP.registerEvent(
    eventId = FUSGroupIds.PACKAGE_REMOVED,
    eventField1 = packageIdField,
    eventField2 = packageVersionField,
    eventField3 = buildSystemField
)
private val packageVersionChangedEvent = GROUP.registerVarargEvent(
    eventId = FUSGroupIds.PACKAGE_VERSION_UPDATED,
    packageIdField, packageFromVersionField, packageVersionField, buildSystemField
)

private val packageScopeChangedEvent = GROUP.registerVarargEvent(
    eventId = FUSGroupIds.PACKAGE_SCOPE_UPDATED,
    packageIdField, packageScopeFromField, packageScopeToField, buildSystemField
)

private val packageVariantChangedEvent = GROUP.registerEvent(
    eventId = FUSGroupIds.PACKAGE_VARIANT_UPDATED,
    eventField1 = packageIdField,
    eventField2 = buildSystemField
)
private val repositoryAddedEvent = GROUP.registerEvent(
    eventId = FUSGroupIds.REPOSITORY_ADDED,
    eventField1 = repositoryIdField,
    eventField2 = repositoryUrlField
)
private val repositoryRemovedEvent = GROUP.registerEvent(
    eventId = FUSGroupIds.REPOSITORY_REMOVED,
    eventField1 = repositoryIdField,
    eventField2 = repositoryUrlField,
    eventField3 = repositoryUsesCustomUrlField
)
private val preferencesRestoreDefaultsEvent = GROUP.registerEvent(FUSGroupIds.PREFERENCES_RESTORE_DEFAULTS)
private val packageSelectedEvent =
    GROUP.registerEvent(eventId = FUSGroupIds.PACKAGE_SELECTED, packageIsInstalledField)
private val targetModulesSelectedEvent = GROUP.registerEvent(
    eventId = FUSGroupIds.MODULES_SELECTED,
    eventField1 = targetModulesCountField,
    eventField2 = targetModulesMixedBuildSystemsField
)
private val detailsLinkClickEvent = GROUP.registerEvent(
    eventId = FUSGroupIds.DETAILS_LINK_CLICK,
    eventField1 = detailsLinkLabelField
)
private val onlyStableToggleEvent = GROUP.registerEvent(
    eventId = FUSGroupIds.TOGGLE,
    eventField1 = toggleValueField,
)
private val searchRequestEvent = GROUP.registerEvent(
    eventId = FUSGroupIds.SEARCH_REQUEST,
    eventField1 = searchQueryLengthField
)
private val searchQueryClearEvent = GROUP.registerEvent(FUSGroupIds.SEARCH_QUERY_CLEAR)
private val upgradeAllEvent = GROUP.registerEvent(FUSGroupIds.UPGRADE_ALL)
private val infoPanelOpenedEvent = GROUP.registerEvent(FUSGroupIds.PANEL_OPENED)
private val goToSourceEvent = GROUP.registerEvent(
    eventId = FUSGroupIds.GO_TO_SOURCE,
    eventField1 = buildSystemField,
    eventField2 = packageIdField
)
private val headerAttributesClick = GROUP.registerEvent(
    FUSGroupIds.HEADER_ATTRIBUTES_CLICK,
    eventField1 = isSearchHeader
)
private val headerVariantClick = GROUP.registerEvent(FUSGroupIds.HEADER_VARIANTS_CLICK)

internal fun PackageSearchFUSEvent.log() {
    when (this) {
        is PackageSearchFUSEvent.PackageInstalled -> logPackageInstalled(packageIdentifier, targetModule)
        is PackageSearchFUSEvent.PackageRemoved -> logPackageRemoved(
            packageIdentifier = packageIdentifier,
            packageVersion = packageVersion,
            targetModule = targetModule
        )

        is PackageSearchFUSEvent.PackageVersionChanged -> logPackageVersionChanged(
            packageIdentifier = packageIdentifier,
            packageFromVersion = packageFromVersion,
            packageTargetVersion = packageTargetVersion,
            targetModule = targetModule
        )

        is PackageSearchFUSEvent.PackageScopeChanged -> logPackageScopeChanged(
            packageIdentifier = packageIdentifier,
            scopeFrom = scopeFrom,
            scopeTo = scopeTo,
            targetModule = targetModule
        )

        is PackageSearchFUSEvent.PackageVariantChanged -> logPackageVariantChanged(
            packageIdentifier = packageIdentifier,
            targetModule = targetModule
        )

        is PackageSearchFUSEvent.RepositoryAdded -> logRepositoryAdded(model)
        is PackageSearchFUSEvent.RepositoryRemoved -> logRepositoryRemoved(model)
        is PackageSearchFUSEvent.PreferencesRestoreDefaults -> logPreferencesRestoreDefaults()
        is PackageSearchFUSEvent.TargetModulesSelected -> logTargetModuleSelected(targetModules)
        is PackageSearchFUSEvent.PackageSelected -> logPackageSelected(isInstalled)
        is PackageSearchFUSEvent.DetailsLinkClick -> logDetailsLinkClick(type)
        is PackageSearchFUSEvent.OnlyStableToggle -> logOnlyStableToggle(state)
        is PackageSearchFUSEvent.SearchRequest -> logSearchRequest(query)
        is PackageSearchFUSEvent.SearchQueryClear -> logSearchQueryClear()
        is PackageSearchFUSEvent.UpgradeAll -> logUpgradeAll()
        is PackageSearchFUSEvent.InfoPanelOpened -> logInfoPanelOpened()
        is PackageSearchFUSEvent.GoToSource -> logGoToSource(module, packageId)
        is PackageSearchFUSEvent.HeaderAttributesClick -> logHeaderAttributesClick(isSearchHeader)
        is PackageSearchFUSEvent.HeaderVariantsClick -> logHeaderVariantsClick()
    }
}

private fun logPackageInstalled(
    packageIdentifier: String,
    targetModule: PackageSearchModule,
) = runSafelyIfEnabled(packageInstalledEvent) {
    log(packageIdentifier, targetModule::class.java)
}

private fun logPackageRemoved(
    packageIdentifier: String,
    packageVersion: String?,
    targetModule: PackageSearchModule,
) = runSafelyIfEnabled(packageRemovedEvent) {
    log(packageIdentifier, packageVersion, targetModule::class.java)
}

private fun logPackageVersionChanged(
    packageIdentifier: String,
    packageFromVersion: String?,
    packageTargetVersion: String,
    targetModule: PackageSearchModule,
) = runSafelyIfEnabled(packageVersionChangedEvent) {
    log(
        packageIdField.with(packageIdentifier),
        packageFromVersionField.with(packageFromVersion),
        packageVersionField.with(packageTargetVersion),
        buildSystemField.with(targetModule::class.java)
    )
}

private fun logPackageVariantChanged(
    packageIdentifier: String,
    targetModule: PackageSearchModule,
) = runSafelyIfEnabled(packageVariantChangedEvent) {
    log(packageIdentifier, targetModule::class.java)
}

private fun logPackageScopeChanged(
    packageIdentifier: String,
    scopeFrom: String?,
    scopeTo: String?,
    targetModule: PackageSearchModule,
) = runSafelyIfEnabled(packageScopeChangedEvent) {
    log(
        packageIdField.with(packageIdentifier),
        packageScopeFromField.with(scopeFrom ?: "[default]"),
        packageScopeToField.with(scopeTo ?: "[default]"),
        buildSystemField.with(targetModule::class.java)
    )
}

private fun logRepositoryAdded(model: ApiRepository) = runSafelyIfEnabled(repositoryAddedEvent) {
    log(FUSGroupIds.IndexedRepositories.forId(model.id), FUSGroupIds.IndexedRepositories.validateUrl(model.url))
}

private fun logRepositoryRemoved(model: ApiRepository) = runSafelyIfEnabled(repositoryRemovedEvent) {
    val repository = FUSGroupIds.IndexedRepositories.forId(model.id)
    val validatedUrl = FUSGroupIds.IndexedRepositories.validateUrl(model.url)
    val usesCustomUrl = repository != FUSGroupIds.IndexedRepositories.NONE &&
            repository != FUSGroupIds.IndexedRepositories.OTHER &&
            validatedUrl == null
    log(repository, validatedUrl, usesCustomUrl)
}

private fun logPreferencesRestoreDefaults() = runSafelyIfEnabled(preferencesRestoreDefaultsEvent) {
    log()
}

private fun logTargetModuleSelected(targetModules: List<PackageSearchModule>) =
    runSafelyIfEnabled(targetModulesSelectedEvent) {
        if (targetModules.isNotEmpty()) {
            log(targetModules.size, targetModules.groupBy { it.identity.group }.keys.size != 1)
        }
    }

private fun logPackageSelected(isInstalled: Boolean) = runSafelyIfEnabled(packageSelectedEvent) {
    log(isInstalled)
}

private fun logDetailsLinkClick(type: FUSGroupIds.DetailsLinkTypes) = runSafelyIfEnabled(detailsLinkClickEvent) {
    log(type)
}

private fun logOnlyStableToggle(state: Boolean) = runSafelyIfEnabled(onlyStableToggleEvent) {
    log(state)
}

private fun logSearchRequest(query: String) = runSafelyIfEnabled(searchRequestEvent) {
    log(query.length)
}

private fun logSearchQueryClear() = runSafelyIfEnabled(searchQueryClearEvent) {
    log()
}

private fun logUpgradeAll() = runSafelyIfEnabled(upgradeAllEvent) {
    log()
}

private fun logInfoPanelOpened() = runSafelyIfEnabled(infoPanelOpenedEvent) {
    log()
}

private fun logGoToSource(
    module: PackageSearchModule,
    packageId: String,
) = runSafelyIfEnabled(goToSourceEvent) {
    log(module::class.java, packageId)
}

private fun logHeaderAttributesClick(isSearchHeader: Boolean) = runSafelyIfEnabled(headerAttributesClick) {
    log(isSearchHeader)
}

private fun logHeaderVariantsClick() = runSafelyIfEnabled(headerVariantClick) {
    log()
}

private fun <T : BaseEventId> runSafelyIfEnabled(event: T, action: T.() -> Unit) {
    if (FUS_ENABLED) {
        try {
            event.action()
        } catch (e: RuntimeException) {
            val throwable = RuntimeExceptionWithAttachments(
                /* message = */ "Non-critical error while logging analytics event. " +
                        "This doesn't impact plugin functionality.",
                /* cause = */ e
            )
            PackageSearchLogger.logError(
                message = PackageSearchBundle.message("packagesearch.logging.error", event.eventId),
                throwable = throwable
            )
        }
    }
}