package com.jetbrains.packagesearch.plugin.gradle.dependencies.report

import com.intellij.remoteDev.util.UrlParameterKeys.Companion.projectPath
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.api.artifacts.component.LibraryComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor
import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability

fun Configuration.toSerializable() = when {
    isCanBeResolved -> SerializableConfigurationDetails.Resolvable(
        name = name,
        attributes = attributes.toMap(),
        dependencies = incoming.resolutionResult.rootComponent.get()
            .dependencies
            .mapNotNull { it.toSerializable() }
    )

    else -> SerializableConfigurationDetails.Unresolvable(
        name = name,
        attributes = attributes.toMap()
    )
}

fun DependencyResult.toSerializable(visited: Set<String> = emptySet()): SerializableDependencyResult? =
    when (this) {
        is ResolvedDependencyResult -> SerializableDependencyResult.Resolved(
            requested = requested.toSerializable(),
            isConstraint = isConstraint,
            selected = selected.toSerializable(visited),
            resolvedVariant = resolvedVariant.toSerializable()
        )

        is UnresolvedDependencyResult -> SerializableDependencyResult.Unresolved(
            requested = requested.toSerializable(),
            isConstraint = isConstraint,
            attempted = attempted.toSerializable(),
            attemptedReason = attemptedReason.toSerializable(),
            failure = failure.toSerializable()
        )

        else -> null
    }

private fun Throwable.toSerializable() = SerializableThrowable(
    message = message ?: "",
    stackTrace = stackTraceToString().lines()
)

private fun ComponentSelector.toSerializable() = when (this) {
    is ProjectComponentSelector -> SerializableComponentSelector.Project(
        displayName = displayName,
        attributes = attributes.toMap(),
        requestedCapabilities = requestedCapabilities.map { it.toSerializable() },
        buildPath = buildPath,
        buildName = buildName,
        projectPath = projectPath
    )

    is ModuleComponentSelector -> SerializableComponentSelector.Module(
        attributes = attributes.toMap(),
        requestedCapabilities = requestedCapabilities.map { it.toSerializable() },
        group = group,
        module = module,
        version = version,
    )

    is LibraryComponentSelector -> SerializableComponentSelector.Library(
        displayName = displayName,
        attributes = attributes.toMap(),
        requestedCapabilities = requestedCapabilities.map { it.toSerializable() },
        projectPath = projectPath,
        libraryName = libraryName,
        variant = variant
    )

    else -> SerializableComponentSelector.Other(
        displayName = displayName,
        attributes = attributes.toMap(),
        requestedCapabilities = requestedCapabilities.map { it.toSerializable() }
    )
}

private fun ResolvedComponentResult.toSerializable(visited: Set<String> = emptySet()) =
    when {
        id.displayName in visited -> SerializableResolvedComponentResult.Visited(
            id = id.toSerializable(),
            selectionReason = selectionReason.toSerializable(),
            moduleVersion = moduleVersion?.toSerializable(),
            variants = kotlin.runCatching { variants.map { it.toSerializable() } }.getOrDefault(emptyList())
        )

        else -> SerializableResolvedComponentResult.New(
            id = id.toSerializable(),
            dependencies = dependencies.mapNotNull { it.toSerializable(visited + id.displayName) },
            selectionReason = selectionReason.toSerializable(),
            moduleVersion = moduleVersion?.toSerializable(),
            variants = kotlin.runCatching { variants.map { it.toSerializable() } }.getOrDefault(emptyList())
        )
    }

private fun ResolvedVariantResult.toSerializable(): SerializableResolvedVariantResult =
    SerializableResolvedVariantResult(
        displayName = displayName,
        attributes = attributes.toMap(),
        capabilities = kotlin.runCatching { capabilities.map { it.toSerializable() } }.getOrDefault(emptyList()),
        owner = kotlin.runCatching { owner.toSerializable() }.getOrNull(),
        externalVariant = kotlin.runCatching { externalVariant.get().toSerializable() }.getOrNull()
    )

private fun Capability.toSerializable() =
    SerializableCapability(
        group = group,
        name = name,
        version = version
    )

private fun AttributeContainer.toMap() = buildMap {
    for (key in keySet()) {
        this[key.name] = getAttribute(key).toString()
    }
}


private fun ModuleVersionIdentifier.toSerializable() =
    SerializableModuleIdentifier(
        group = group,
        module = name
    )

private fun ComponentSelectionReason.toSerializable() =
    SerializableComponentSelectorReason(
        isForced = isForced,
        isConflictResolution = isConflictResolution,
        isSelectedByRule = isSelectedByRule,
        isExpected = isExpected,
        isCompositeSubstitution = kotlin.runCatching { isCompositeSubstitution }.getOrDefault(false),
        isConstrained = kotlin.runCatching { isConstrained }.getOrDefault(false),
        descriptions = kotlin.runCatching { this.descriptions.map { it.toSerializable() } }
            .getOrDefault(emptyList())
    )

private fun ComponentSelectionDescriptor.toSerializable() =
    SerializableComponentSelectionDescriptor(
        cause = cause.toSerializable(),
        description = description
    )

private fun ComponentSelectionCause.toSerializable() = when (this) {
    ComponentSelectionCause.REQUESTED -> SerializableComponentSelectionCause.REQUESTED
    ComponentSelectionCause.SELECTED_BY_RULE -> SerializableComponentSelectionCause.SELECTED_BY_RULE
    ComponentSelectionCause.FORCED -> SerializableComponentSelectionCause.FORCED
    ComponentSelectionCause.CONFLICT_RESOLUTION -> SerializableComponentSelectionCause.CONFLICT_RESOLUTION
    ComponentSelectionCause.COMPOSITE_BUILD -> SerializableComponentSelectionCause.COMPOSITE_BUILD
    ComponentSelectionCause.REJECTION -> SerializableComponentSelectionCause.REJECTION
    ComponentSelectionCause.CONSTRAINT -> SerializableComponentSelectionCause.CONSTRAINT
    ComponentSelectionCause.BY_ANCESTOR -> SerializableComponentSelectionCause.BY_ANCESTOR
    ComponentSelectionCause.ROOT -> SerializableComponentSelectionCause.ROOT
}

private fun ComponentIdentifier.toSerializable() = when (this) {
    is ProjectComponentIdentifier -> toSerializable()
    is ModuleComponentIdentifier -> toSerializable()
    is LibraryBinaryIdentifier -> toSerializable()
    else -> SerializableComponentIdentifier.Other(displayName)
}

private fun LibraryBinaryIdentifier.toSerializable() =
    SerializableComponentIdentifier.LibraryBinary(
        displayName = displayName,
        projectPath = projectPath,
        libraryName = libraryName,
        variant = variant
    )

private fun ProjectComponentIdentifier.toSerializable() =
    SerializableComponentIdentifier.Project(
        displayName = displayName,
        build = build.toSerializable(),
        projectPath = projectPath,
        projectName = kotlin.runCatching { projectName }.getOrNull()
    )

private fun BuildIdentifier.toSerializable() =
    SerializableBuildIdentifier(
        buildPath = kotlin.runCatching { buildPath }.getOrNull(),
        name = name
    )

private fun ModuleComponentIdentifier.toSerializable() =
    SerializableComponentIdentifier.Module(
        group = group,
        module = module,
        version = version,
        moduleIdentifier = kotlin.runCatching { moduleIdentifier.toSerializable() }.getOrNull()
    )

private fun ModuleIdentifier.toSerializable() =
    SerializableModuleIdentifier(
        group = group,
        module = name
    )

