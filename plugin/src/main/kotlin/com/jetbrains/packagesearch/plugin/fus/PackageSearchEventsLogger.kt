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
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.utils.logError
import org.jetbrains.idea.reposearch.statistics.TopPackageIdValidationRule
import org.jetbrains.packagesearch.api.v3.ApiRepository

private const val FUS_ENABLED = true

class PackageSearchEventsLogger : CounterUsagesCollector() {
    override fun getGroup() = GROUP
}

private const val VERSION = 13

private val GROUP = EventLogGroup(FUSGroupIds.GROUP_ID, VERSION)

// FIELDS
private val buildSystemField = EventFields.Class(FUSGroupIds.MODULE_OPERATION_PROVIDER_CLASS)
private val packageIdField =
    EventFields.StringValidatedByCustomRule(FUSGroupIds.PACKAGE_ID, TopPackageIdValidationRule::class.java)
private val packageVersionField =
    EventFields.StringValidatedByRegexpReference(FUSGroupIds.PACKAGE_VERSION, regexpRef = "version")
private val packageFromVersionField =
    EventFields.StringValidatedByRegexpReference(FUSGroupIds.PACKAGE_FROM_VERSION, regexpRef = "version")
private val repositoryIdField = EventFields.Enum<FUSGroupIds.IndexedRepositories>(FUSGroupIds.REPOSITORY_ID)
private val repositoryUrlField =
    EventFields.String(FUSGroupIds.REPOSITORY_URL, allowedValues = FUSGroupIds.indexedRepositoryUrls)
private val repositoryUsesCustomUrlField = EventFields.Boolean(FUSGroupIds.REPOSITORY_USES_CUSTOM_URL)
private val packageIsInstalledField = EventFields.Boolean(FUSGroupIds.PACKAGE_IS_INSTALLED)
private val targetModulesCountField = EventFields.Int(FUSGroupIds.TARGET_MODULES)
private val targetModulesMixedBuildSystemsField =
    EventFields.Boolean(FUSGroupIds.TARGET_MODULES_MIXED_BUILD_SYSTEMS)
val preferencesGradleScopeCountField = EventFields.Int(FUSGroupIds.PREFERENCES_GRADLE_SCOPES_COUNT)
val preferencesUpdateScopesOnUsageField = EventFields.Boolean(FUSGroupIds.PREFERENCES_UPDATE_SCOPES_ON_USAGE)
val preferencesDefaultGradleScopeChangedField =
    EventFields.Boolean(FUSGroupIds.PREFERENCES_DEFAULT_GRADLE_SCOPE_CHANGED)
val preferencesDefaultMavenScopeChangedField =
    EventFields.Boolean(FUSGroupIds.PREFERENCES_DEFAULT_MAVEN_SCOPE_CHANGED)
internal val preferencesAutoAddRepositoriesField =
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
private val packageScopeChangedEvent = GROUP.registerEvent(
    eventId = FUSGroupIds.PACKAGE_SCOPE_UPDATED,
    eventField1 = packageIdField,
    eventField2 = buildSystemField
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
fun logPackageInstalled(
    packageIdentifier: String,
    targetModule: PackageSearchModule,
) = runSafelyIfEnabled(packageInstalledEvent) {
    log(packageIdentifier, targetModule::class.java)
}

fun logPackageRemoved(
    packageIdentifier: String,
    packageVersion: String?,
    targetModule: PackageSearchModule,
) = runSafelyIfEnabled(packageRemovedEvent) {
    log(packageIdentifier, packageVersion, targetModule::class.java)
}

fun logPackageVersionChanged(
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

fun logPackageVariantChanged(
    packageIdentifier: String,
    targetModule: PackageSearchModule,
) = runSafelyIfEnabled(packageVariantChangedEvent) {
    log(packageIdentifier, targetModule::class.java)
}

fun logPackageScopeChanged(
    packageIdentifier: String,
    targetModule: PackageSearchModule,
) = runSafelyIfEnabled(packageScopeChangedEvent) {
    log(packageIdentifier, targetModule::class.java)
}

fun logRepositoryAdded(model: ApiRepository) = runSafelyIfEnabled(repositoryAddedEvent) {
    log(FUSGroupIds.IndexedRepositories.forId(model.id), FUSGroupIds.IndexedRepositories.validateUrl(model.url))
}

fun logRepositoryRemoved(model: ApiRepository) = runSafelyIfEnabled(repositoryRemovedEvent) {
    val repository = FUSGroupIds.IndexedRepositories.forId(model.id)
    val validatedUrl = FUSGroupIds.IndexedRepositories.validateUrl(model.url)
    val usesCustomUrl = repository != FUSGroupIds.IndexedRepositories.NONE &&
            repository != FUSGroupIds.IndexedRepositories.OTHER &&
            validatedUrl == null
    log(repository, validatedUrl, usesCustomUrl)
}

fun logPreferencesRestoreDefaults() = runSafelyIfEnabled(preferencesRestoreDefaultsEvent) {
    log()
}

fun logTargetModuleSelected(targetModules: List<PackageSearchModule>) =
    runSafelyIfEnabled(targetModulesSelectedEvent) {
        if (targetModules.isNotEmpty()) {
            log(targetModules.size, targetModules.groupBy { it.identity.group }.keys.size != 1)
        }
    }

fun logPackageSelected(isInstalled: Boolean) = runSafelyIfEnabled(packageSelectedEvent) {
    log(isInstalled)
}

fun logDetailsLinkClick(type: FUSGroupIds.DetailsLinkTypes) = runSafelyIfEnabled(detailsLinkClickEvent) {
    log(type)
}

fun logOnlyStableToggle(state: Boolean) = runSafelyIfEnabled(onlyStableToggleEvent) {
    log(state)
}

fun logSearchRequest(query: String) = runSafelyIfEnabled(searchRequestEvent) {
    log(query.length)
}

fun logSearchQueryClear() = runSafelyIfEnabled(searchQueryClearEvent) {
    log()
}

fun logUpgradeAll() = runSafelyIfEnabled(upgradeAllEvent) {
    log()
}

fun logInfoPanelOpened() = runSafelyIfEnabled(infoPanelOpenedEvent) {
    log()
}

fun logGoToSource(
    module: PackageSearchModule,
    packageId: String,
) = runSafelyIfEnabled(goToSourceEvent) {
    log(module::class.java, packageId)
}

fun logHeaderAttributesClick(isSearchHeader: Boolean) = runSafelyIfEnabled(headerAttributesClick) {
    log(isSearchHeader)
}

fun logHeaderVariantsClick() = runSafelyIfEnabled(headerVariantClick) {
    log()
}

private fun <T : BaseEventId> runSafelyIfEnabled(event: T, action: T.() -> Unit) {
    if (FUS_ENABLED) {
        try {
            event.action()
        } catch (e: RuntimeException) {
            logError(
                message = PackageSearchBundle.message("packagesearch.logging.error", event.eventId),
                throwable = RuntimeExceptionWithAttachments(
                    "Non-critical error while logging analytics event. This doesn't impact plugin functionality.",
                    e
                )
            )
        }
    }
}