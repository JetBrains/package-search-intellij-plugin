@file:OptIn(ExperimentalSerializationApi::class)

package org.jetbrains.packagesearch.gradle.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SerializableConfigurationDetails(
    val name: String,
    override val directory: SerializablePath,
    private val attributesRef: Int? = null,
    private val dependencyRefs: List<Int> = emptyList(),
) : FileSystemDistributed {

    val attributes: Map<String, String>
        get() = loadAttributes(attributesRef)

    val dependencies: List<SerializableDependencyResult>
        get() = dependencyRefs.map { loadDependencyResult(it) }

}

@Serializable
sealed interface SerializableDependencyResult : FileSystemDistributed {

    companion object {
        const val DIR_NAME = "dependencyResults"
    }

    val requested: SerializableComponentSelector
    val isConstraint: Boolean

    @Serializable
    @SerialName("resolved")
    data class Resolved(
        private val requestedRef: Int,
        override val isConstraint: Boolean = true,
        override val directory: SerializablePath,
        private val selectedRef: Int,
        private val resolvedVariantRef: Int,
    ) : SerializableDependencyResult {
        override val requested: SerializableComponentSelector
            get() = loadComponentSelector(requestedRef)

        val selected: SerializableResolvedComponentResult
            get() = loadResolvedComponentResult(selectedRef)

        val resolvedVariant: SerializableResolvedVariantResult
            get() = loadResolvedVariantResult(resolvedVariantRef)
    }

    @Serializable
    @SerialName("unresolved")
    data class Unresolved(
        private val requestedRef: Int,
        override val isConstraint: Boolean = false,
        override val directory: SerializablePath,
        private val attemptedRef: Int,
        private val attemptedReasonRef: Int,
        private val failureRef: Int? = null,
    ) : SerializableDependencyResult {
        override val requested: SerializableComponentSelector
            get() = loadComponentSelector(requestedRef)

        val attempted: SerializableComponentSelector
            get() = loadComponentSelector(attemptedRef)

        val attemptedReason: SerializableComponentSelectorReason
            get() = loadComponentSelectionReason(attemptedReasonRef)

        val failure: SerializableThrowable?
            get() = failureRef?.let { loadFailure(it) }


    }
}

@Serializable
sealed interface SerializableResolvedComponentResult : FileSystemDistributed {

    companion object {
        const val DIR_NAME = "resolvedComponentResults"
    }

    val id: SerializableComponentIdentifier
    val selectionReason: SerializableComponentSelectorReason
    val moduleVersion: SerializableModuleIdentifier?
    val variants: List<SerializableResolvedVariantResult>

    @Serializable
    @SerialName("new")
    data class New(
        private val idRef: Int,
        private val selectionReasonRef: Int,
        private val moduleVersionRef: Int? = null,
        override val directory: SerializablePath,
        private val variantRefs: List<Int> = emptyList(),
        private val dependencyRefs: List<Int> = emptyList(),
    ) : SerializableResolvedComponentResult {
        override val id: SerializableComponentIdentifier
            get() = loadComponentIdentifier(idRef)

        override val selectionReason: SerializableComponentSelectorReason
            get() = loadComponentSelectionReason(selectionReasonRef)

        override val moduleVersion: SerializableModuleIdentifier?
            get() = moduleVersionRef?.let { loadModuleIdentifier(it) }

        override val variants: List<SerializableResolvedVariantResult>
            get() = variantRefs.map { loadResolvedVariantResult(it) }

        val dependencies: List<SerializableDependencyResult>
            get() = dependencyRefs.map { loadDependencyResult(it) }
    }

    @Serializable
    @SerialName("visited")
    data class Visited(
        private val idRef: Int,
        override val directory: SerializablePath,
        private val selectionReasonRef: Int,
        private val moduleVersionRef: Int? = null,
        private val variantRefs: List<Int> = emptyList(),
    ) : SerializableResolvedComponentResult {
        override val id: SerializableComponentIdentifier
            get() = loadComponentIdentifier(idRef)

        override val selectionReason: SerializableComponentSelectorReason
            get() = loadComponentSelectionReason(selectionReasonRef)

        override val moduleVersion: SerializableModuleIdentifier?
            get() = moduleVersionRef?.let { loadModuleIdentifier(it) }

        override val variants: List<SerializableResolvedVariantResult>
            get() = variantRefs.map { loadResolvedVariantResult(it) }

    }

}

@Serializable
data class SerializableResolvedVariantResult(
    override val directory: SerializablePath,
    val displayName: String,
    private val attributesRef: Int? = null,
    private val capabilityRefs: List<Int> = emptyList(),
) : FileSystemDistributed {

    companion object {
        const val DIR_NAME = "resolvedVariantResults"
    }

    val attributes: Map<String, String>
        get() = loadAttributes(attributesRef)

    val capabilities: List<SerializableCapability>
        get() = capabilityRefs.map { loadCapability(it) }
}

@Serializable
sealed interface SerializableComponentIdentifier {

    companion object {
        const val DIR_NAME = "componentIdentifiers"
    }

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
    ) : SerializableComponentIdentifier {
        override val displayName: String
            get() = "$group:$module:$version"

        val moduleIdentifier: SerializableModuleIdentifier
            get() = SerializableModuleIdentifier(group, module)
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
) {
    companion object {
        const val DIR_NAME = "moduleIdentifiers"
    }
}

@Serializable
data class SerializableBuildIdentifier(
    val buildPath: String? = null,
    val name: String,
)

@Serializable
data class SerializableThrowable(
    val message: String? = null,
    val stackTrace: List<String> = emptyList(),
) {
    companion object {
        const val DIR_NAME = "failures"
    }
}

@Serializable
data class SerializableComponentSelectorReason(
    val isForced: Boolean = false,
    val isConflictResolution: Boolean = false,
    val isSelectedByRule: Boolean = false,
    val isExpected: Boolean = false,
    val isCompositeSubstitution: Boolean = false,
    val isConstrained: Boolean = false,
    private val descriptionRefs: List<Int> = emptyList(),
    override val directory: SerializablePath,
) : FileSystemDistributed {

    companion object {
        const val DIR_NAME = "componentSelectorReasons"
    }

    val descriptions: List<SerializableComponentSelectionDescriptor>
        get() = descriptionRefs.map { loadComponentSelectionDescriptor(it) }
}

@Serializable
data class SerializableComponentSelectionDescriptor(
    val cause: SerializableComponentSelectionCause,
    val description: String,
    override val directory: SerializablePath,
) : FileSystemDistributed {

    companion object {
        const val DIR_NAME = "componentSelectionDescriptors"
    }
}

@Serializable
enum class SerializableComponentSelectionCause {
    ROOT, REQUESTED, SELECTED_BY_RULE, FORCED, CONFLICT_RESOLUTION,
    COMPOSITE_BUILD, REJECTION, CONSTRAINT, BY_ANCESTOR;
}

@Serializable
sealed interface SerializableComponentSelector : FileSystemDistributed {

    companion object {
        const val DIR_NAME = "componentSelectors"
    }

    val displayName: String
    val attributes: Map<String, String>
    val requestedCapabilities: List<SerializableCapability>

    @Serializable
    @SerialName("library")
    data class Library(
        override val displayName: String,
        private val attributesRef: Int? = null,
        private val requestedCapabilityRefs: List<Int> = emptyList(),
        override val directory: SerializablePath,
        val projectPath: String,
        val libraryName: String? = null,
        val variant: String? = null,
    ) : SerializableComponentSelector {
        override val attributes: Map<String, String>
            get() = loadAttributes(attributesRef)

        override val requestedCapabilities: List<SerializableCapability>
            get() = requestedCapabilityRefs.map { loadCapability(it) }
    }

    @Serializable
    @SerialName("module")
    data class Module(
        private val attributesRef: Int? = null,
        private val requestedCapabilityRefs: List<Int> = emptyList(),
        override val directory: SerializablePath,
        val group: String,
        val module: String,
        val version: String,
        private val versionConstraintRef: Int? = null,
    ) : SerializableComponentSelector {
        override val displayName: String
            get() = "$group:$module:$version"

        override val attributes: Map<String, String>
            get() = loadAttributes(attributesRef)

        override val requestedCapabilities: List<SerializableCapability>
            get() = requestedCapabilityRefs.map { loadCapability(it) }

        val versionConstraint: SerializableVersionConstraint?
            get() = versionConstraintRef?.let { loadVersionConstraint(it) }
    }

    @Serializable
    @SerialName("project")
    data class Project(
        override val displayName: String,
        private val attributesRef: Int,
        private val requestedCapabilityRefs: List<Int> = emptyList(),
        val buildPath: String? = null,
        val buildName: String,
        val projectPath: String,
        override val directory: SerializablePath,
    ) : SerializableComponentSelector {
        override val attributes: Map<String, String>
            get() = loadAttributes(attributesRef)

        override val requestedCapabilities: List<SerializableCapability>
            get() = requestedCapabilityRefs.map { loadCapability(it) }
    }

    @Serializable
    @SerialName("unversionedModule")
    data class UnversionedModule(
        private val attributesRef: Int,
        private val requestedCapabilityRef: List<Int> = emptyList(),
        val group: String,
        val module: String,
        override val directory: SerializablePath,
    ) : SerializableComponentSelector {
        override val displayName: String
            get() = "$group:$module:*"

        override val attributes: Map<String, String>
            get() = loadAttributes(attributesRef)

        override val requestedCapabilities: List<SerializableCapability>
            get() = requestedCapabilityRef.map { loadCapability(it) }
    }

    @Serializable
    @SerialName("other")
    data class Other(
        override val displayName: String,
        private val attributesRef: Int,
        private val requestedCapabilityRef: List<Int> = emptyList(),
        override val directory: SerializablePath,
    ) : SerializableComponentSelector {
        override val attributes: Map<String, String>
            get() = loadAttributes(attributesRef)

        override val requestedCapabilities: List<SerializableCapability>
            get() = requestedCapabilityRef.map { loadCapability(it) }
    }
}

@Serializable
data class SerializableVersionConstraint(
    val requiredVersion: String,
    val branch: String? = null,
    val preferredVersion: String = requiredVersion,
    val strictVersion: String = requiredVersion,
    val rejectedVersions: List<String> = emptyList(),
) {
    companion object {
        const val DIR_NAME = "versionConstraints"
    }
}

@Serializable
data class SerializableCapability(
    val group: String,
    val name: String,
    val version: String? = null,
) {
    companion object {
        const val DIR_NAME = "capabilities"
    }
}