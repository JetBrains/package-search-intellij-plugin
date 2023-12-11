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

package com.jetbrains.packagesearch.plugin.fus

object FUSGroupIds {

    const val GROUP_ID = "packagesearch"

    // FIELDS
    const val IS_SEARCH_HEADER = "is_search_header"
    const val PANEL_OPENED = "info_panel_opened"
    const val GO_TO_SOURCE = "got_to_source"
    const val HEADER_ATTRIBUTES_CLICK = "header_attributes_click"
    const val HEADER_VARIANTS_CLICK = "header_variants_click"
    const val MODULE_OPERATION_PROVIDER_CLASS = "module_operation_provider_class"
    const val PACKAGE_ID = "package_id"
    const val PACKAGE_VERSION = "package_version"
    const val PACKAGE_FROM_VERSION = "package_from_version"
    const val REPOSITORY_ID = "repository_id"
    const val REPOSITORY_URL = "repository_url"
    const val REPOSITORY_USES_CUSTOM_URL = "repository_uses_custom_url"
    const val PACKAGE_IS_INSTALLED = "package_is_installed"
    const val TARGET_MODULES = "target_modules"
    const val TARGET_MODULES_MIXED_BUILD_SYSTEMS = "target_modules_mixed_build_systems"

    const val PREFERENCES_GRADLE_SCOPES_COUNT = "preferences_gradle_scopes_count"
    const val PREFERENCES_UPDATE_SCOPES_ON_USAGE = "preferences_update_scopes_on_usage"
    const val PREFERENCES_DEFAULT_GRADLE_SCOPE_CHANGED = "preferences_default_gradle_scope_changed"
    const val PREFERENCES_DEFAULT_MAVEN_SCOPE_CHANGED = "preferences_default_maven_scope_changed"
    const val PREFERENCES_AUTO_ADD_REPOSITORIES = "preferences_auto_add_repositories"
    const val DETAILS_LINK_LABEL = "details_link_label"
    const val CHECKBOX_NAME = "checkbox_name"
    const val CHECKBOX_STATE = "checkbox_state"
    const val SEARCH_QUERY_LENGTH = "search_query_length"
    const val SORT_METRIC = "sort_metric"

    enum class DetailsLinkTypes { Scm, Documentation, License, ProjectWebsite, Readme }

    enum class IndexedRepositories(val ids: Set<String>, val urls: Set<String>) {
        OTHER(ids = emptySet(), urls = emptySet()),
        NONE(ids = emptySet(), urls = emptySet()),
        MAVEN_CENTRAL(
            ids = setOf("maven_central"),
            urls = setOf(
                "https://repo.maven.apache.org/maven2/",
                "https://maven-central.storage-download.googleapis.com/maven2",
                "https://repo1.maven.org/maven2/"
            )
        ),
        GOOGLE_MAVEN(
            ids = setOf("gmaven"),
            urls = setOf("https://maven.google.com/")
        ),
        JETBRAINS_REPOS(
            ids = setOf("dokka_dev", "compose_dev", "ktor_eap", "space_sdk"),
            urls = setOf(
                "https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev/",
                "https://maven.pkg.jetbrains.space/public/p/compose/dev/",
                "https://maven.pkg.jetbrains.space/public/p/ktor/eap/",
                "https://maven.pkg.jetbrains.space/public/p/space/maven/"
            )
        ),
        CLOJARS(
            ids = setOf("clojars"),
            urls = setOf("https://repo.clojars.org/")
        );

        companion object {

            fun forId(repositoryId: String?): IndexedRepositories {
                if (repositoryId.isNullOrBlank()) return NONE
                return entries.find { repositoryId in it.ids } ?: OTHER
            }

            fun validateUrl(repositoryUrl: String?): String? {
                if (repositoryUrl.isNullOrBlank()) return null
                return if (repositoryUrl in indexedRepositoryUrls) repositoryUrl else null
            }
        }
    }

    val indexedRepositoryUrls = IndexedRepositories.entries
        .flatMap { it.urls }

    // EVENTS
    const val PACKAGE_INSTALLED = "package_installed"
    const val PACKAGE_REMOVED = "package_removed"
    const val PACKAGE_VERSION_UPDATED = "package_version_updated"
    const val PACKAGE_SCOPE_UPDATED = "package_scope_updated"
    const val PACKAGE_VARIANT_UPDATED = "package_variant_updated"
    const val REPOSITORY_ADDED = "repository_added"
    const val REPOSITORY_REMOVED = "repository_removed"
    const val PREFERENCES_CHANGED = "preferences_changed"
    const val PREFERENCES_RESTORE_DEFAULTS = "preferences_restore_defaults"
    const val PACKAGE_SELECTED = "package_selected"
    const val MODULES_SELECTED = "modules_selected"
    const val DETAILS_LINK_CLICK = "details_link_click"
    const val TOGGLE = "toggle"
    const val SORT_METRIC_CHANGED = "sort_metric_changed"
    const val SEARCH_REQUEST = "search_request"
    const val SEARCH_QUERY_CLEAR = "search_query_clear"
    const val UPGRADE_ALL = "upgrade_all_event"

    // VALIDATORS
    const val RULE_TOP_PACKAGE_ID = "top_package_id"
}
