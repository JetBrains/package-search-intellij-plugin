package com.jetbrains.packagesearch.plugin.gradle.dependencies.report

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SerializableConfigurationDetails {
    val name: String
    val attributes: Map<String, String>

    @Serializable
    @SerialName("resolvable")
    data class Resolvable(
        override val name: String,
        override val attributes: Map<String, String> = emptyMap(),
        val dependencies: List<SerializableDependencyResult> = emptyList(),
    ) : SerializableConfigurationDetails

    @Serializable
    @SerialName("unresolvable")
    data class Unresolvable(
        override val name: String,
        override val attributes: Map<String, String> = emptyMap(),
    ) : SerializableConfigurationDetails

}

@Serializable
sealed interface SerializableDependencyResult {

    val requested: SerializableComponentSelector
    val isConstraint: Boolean

    @Serializable
    @SerialName("resolved")
    data class Resolved(
        override val requested: SerializableComponentSelector,
        override val isConstraint: Boolean = false,
        val selected: SerializableResolvedComponentResult,
        val resolvedVariant: SerializableResolvedVariantResult,
    ) : SerializableDependencyResult

    @Serializable
    @SerialName("unresolved")
    data class Unresolved(
        override val requested: SerializableComponentSelector,
        override val isConstraint: Boolean = false,
        val attempted: SerializableComponentSelector,
        val attemptedReason: SerializableComponentSelectorReason,
        val failure: SerializableThrowable,
    ) : SerializableDependencyResult
}

@Serializable
sealed interface SerializableResolvedComponentResult {

    val id: SerializableComponentIdentifier
    val selectionReason: SerializableComponentSelectorReason
    val moduleVersion: SerializableModuleIdentifier?
    val variants: List<SerializableResolvedVariantResult>

    @Serializable
    @SerialName("new")
    data class New(
        override val id: SerializableComponentIdentifier,
        override val selectionReason: SerializableComponentSelectorReason,
        override val moduleVersion: SerializableModuleIdentifier? = when (id) {
            is SerializableComponentIdentifier.Module -> id.moduleIdentifier
            else -> null
        },
        override val variants: List<SerializableResolvedVariantResult> = emptyList(),
        val dependencies: List<SerializableDependencyResult> = emptyList(),
    ) : SerializableResolvedComponentResult

    @Serializable
    @SerialName("visited")
    data class Visited(
        override val id: SerializableComponentIdentifier,
        override val selectionReason: SerializableComponentSelectorReason,
        override val moduleVersion: SerializableModuleIdentifier? = null,
        override val variants: List<SerializableResolvedVariantResult> = emptyList(),
    ) : SerializableResolvedComponentResult

}

@Serializable
data class SerializableResolvedVariantResult(
    val displayName: String,
    val attributes: Map<String, String>,
    val capabilities: List<SerializableCapability> = emptyList(),
    val owner: SerializableComponentIdentifier? = null,
    val externalVariant: SerializableResolvedVariantResult? = null,
)

@Serializable
sealed interface SerializableComponentIdentifier {
    val displayName: String

    @Serializable
    @SerialName("project")
    data class Project(
        override val displayName: String,
        val build: SerializableBuildIdentifier,
        val projectPath: String,
        val projectName: String? = null,
    ) : SerializableComponentIdentifier

    @Serializable
    @SerialName("module")
    data class Module(
        val group: String,
        val module: String,
        val version: String,
        val moduleIdentifier: SerializableModuleIdentifier? = SerializableModuleIdentifier(group, module),
    ) : SerializableComponentIdentifier {
        override val displayName: String
            get() = "$group:$module:$version"
    }

    @Serializable
    @SerialName("libraryBinary")
    data class LibraryBinary(
        override val displayName: String,
        val projectPath: String,
        val libraryName: String,
        val variant: String,
    ) : SerializableComponentIdentifier

    @Serializable
    @SerialName("other")
    data class Other(override val displayName: String) : SerializableComponentIdentifier
}

@Serializable
data class SerializableModuleIdentifier(
    val group: String,
    val module: String,
)

@Serializable
data class SerializableBuildIdentifier(
    val buildPath: String? = null,
    val name: String,
)

@Serializable
data class SerializableThrowable(
    val message: String,
    val stackTrace: List<String> = emptyList(),
)

@Serializable
data class SerializableComponentSelectorReason(
    val isForced: Boolean = false,
    val isConflictResolution: Boolean = false,
    val isSelectedByRule: Boolean = false,
    val isExpected: Boolean = false,
    val isCompositeSubstitution: Boolean = false,
    val isConstrained: Boolean = false,
    val descriptions: List<SerializableComponentSelectionDescriptor> = emptyList(),
)

@Serializable
data class SerializableComponentSelectionDescriptor(
    val cause: SerializableComponentSelectionCause,
    val description: String,
)

@Serializable
enum class SerializableComponentSelectionCause {
    ROOT, REQUESTED, SELECTED_BY_RULE, FORCED, CONFLICT_RESOLUTION,
    COMPOSITE_BUILD, REJECTION, CONSTRAINT, BY_ANCESTOR;
}

@Serializable
sealed interface SerializableComponentSelector {
    val displayName: String
    val attributes: Map<String, String>
    val requestedCapabilities: List<SerializableCapability>

    @Serializable
    @SerialName("library")
    data class Library(
        override val displayName: String,
        override val attributes: Map<String, String> = emptyMap(),
        override val requestedCapabilities: List<SerializableCapability> = emptyList(),
        val projectPath: String,
        val libraryName: String? = null,
        val variant: String? = null,
    ) : SerializableComponentSelector

    @Serializable
    @SerialName("module")
    data class Module(
        override val attributes: Map<String, String> = emptyMap(),
        override val requestedCapabilities: List<SerializableCapability> = emptyList(),
        val group: String,
        val module: String,
        val version: String,
        val versionConstraint: SerializableVersionConstraint = SerializableVersionConstraint(version),
    ) : SerializableComponentSelector {
        override val displayName: String
            get() = "$group:$module:$version"
    }

    @Serializable
    @SerialName("project")
    data class Project(
        override val displayName: String,
        override val attributes: Map<String, String> = emptyMap(),
        override val requestedCapabilities: List<SerializableCapability> = emptyList(),
        val buildPath: String? = null,
        val buildName: String,
        val projectPath: String,
    ) : SerializableComponentSelector

    @Serializable
    @SerialName("unversionedModule")
    data class UnversionedModule(
        override val attributes: Map<String, String> = emptyMap(),
        override val requestedCapabilities: List<SerializableCapability> = emptyList(),
        val group: String,
        val module: String,
    ) : SerializableComponentSelector {
        override val displayName: String
            get() = "$group:$module:*"
    }

    @Serializable
    @SerialName("other")
    data class Other(
        override val displayName: String,
        override val attributes: Map<String, String> = emptyMap(),
        override val requestedCapabilities: List<SerializableCapability> = emptyList(),
    ) : SerializableComponentSelector
}

@Serializable
data class SerializableVersionConstraint(
    val requiredVersion: String,
    val branch: String? = null,
    val preferredVersion: String = requiredVersion,
    val strictVersion: String = requiredVersion,
    val rejectedVersions: List<String> = emptyList(),
)

@Serializable
data class SerializableCapability(
    val group: String,
    val name: String,
    val version: String? = null,
)