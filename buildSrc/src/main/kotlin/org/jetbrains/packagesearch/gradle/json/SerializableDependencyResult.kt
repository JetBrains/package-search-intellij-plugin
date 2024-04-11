@file:OptIn(ExperimentalSerializationApi::class)

package org.jetbrains.packagesearch.gradle.json

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.VersionConstraint
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

data class EncodingContext(
    val outputDirectory: Path,
    val json: Json,
) {
    fun getFile(ref: Int, dir: String) = outputDirectory.resolve(dir)
        .createDirectories()
        .resolve("$ref.json")
        .apply { if (!exists()) createFile() }
}

fun Configuration.toSerializable(context: EncodingContext) = SerializableConfigurationDetails(
    name = name,
    directory = context.outputDirectory,
    attributesRef = attributes.toMap(context),
    dependencyRefs = incoming.resolutionResult.root.dependencies.mapNotNull { it.toSerializable(context, emptySet()) }
)


fun DependencyResult.toSerializable(context: EncodingContext, visited: Set<String>): Int? {
    val result = when (this) {
        is ResolvedDependencyResult -> SerializableDependencyResult.Resolved(
            requestedRef = requested.toSerializable(context),
            isConstraint = isConstraint,
            directory = context.outputDirectory,
            selectedRef = selected.toSerializable(visited, context),
            resolvedVariantRef = resolvedVariant.toSerializable(context),
        )

        is UnresolvedDependencyResult -> SerializableDependencyResult.Unresolved(
            requestedRef = requested.toSerializable(context),
            isConstraint = isConstraint,
            directory = context.outputDirectory,
            attemptedRef = attempted.toSerializable(context),
            attemptedReasonRef = attemptedReason.toSerializable(context),
            failureRef = failure.toSerializable(context)
        )

        else -> return null
    }
    val hashCode = result.hashCode()
    context.getFile(hashCode, SerializableDependencyResult.DIR_NAME)
        .writeText(context.json.encodeToString(result))
    return hashCode
}


private fun Throwable.toSerializable(context: EncodingContext): Int {
    val throwable = SerializableThrowable(
        message = message,
        stackTrace = stackTraceToString().lines()
    )
    val hashCode = throwable.hashCode()
    context.getFile(hashCode, SerializableThrowable.DIR_NAME)
        .writeText(context.json.encodeToString(throwable))
    return hashCode
}


private fun ComponentSelector.toSerializable(context: EncodingContext): Int {
    val selector = when (this) {
        is ProjectComponentSelector -> SerializableComponentSelector.Project(
            displayName = displayName,
            attributesRef = attributes.toMap(context),
            requestedCapabilityRefs = requestedCapabilities.map { it.toSerializable(context) },
            buildPath = kotlin.runCatching { buildPath }.getOrNull(),
            buildName = buildName,
            projectPath = projectPath,
            directory = context.outputDirectory
        )

        is ModuleComponentSelector -> SerializableComponentSelector.Module(
            attributesRef = attributes.toMap(context),
            requestedCapabilityRefs = requestedCapabilities.map { it.toSerializable(context) },
            directory = context.outputDirectory,
            group = group,
            module = module,
            version = version,
            versionConstraintRef = kotlin.runCatching { versionConstraint.toSerializable(context) }.getOrNull()
        )

        is LibraryComponentSelector -> SerializableComponentSelector.Library(
            displayName = displayName,
            attributesRef = attributes.toMap(context),
            requestedCapabilityRefs = requestedCapabilities.map { it.toSerializable(context) },
            projectPath = projectPath,
            libraryName = libraryName,
            variant = variant,
            directory = context.outputDirectory
        )

        else -> SerializableComponentSelector.Other(
            displayName = displayName,
            attributesRef = attributes.toMap(context),
            requestedCapabilityRef = requestedCapabilities.map { it.toSerializable(context) },
            directory = context.outputDirectory
        )
    }
    val hashCode = selector.hashCode()
    context.getFile(hashCode, SerializableComponentSelector.DIR_NAME)
        .writeText(context.json.encodeToString(selector))
    return hashCode
}


private fun VersionConstraint.toSerializable(context: EncodingContext): Int {
    val constraint = SerializableVersionConstraint(
        requiredVersion = requiredVersion,
        branch = branch,
        preferredVersion = preferredVersion,
        strictVersion = strictVersion,
        rejectedVersions = rejectedVersions
    )
    val hashCode = constraint.hashCode()
    val outputFile =
        context.outputDirectory.resolve("${SerializableVersionConstraint.DIR_NAME}/$hashCode.json")
            .writeText(context.json.encodeToString(constraint))
    return hashCode
}


private fun ResolvedComponentResult.toSerializable(visited: Set<String> = emptySet(), context: EncodingContext): Int {
    val resolvedComponentResult = when {
        id.displayName in visited -> SerializableResolvedComponentResult.Visited(
            idRef = id.toSerializable(context),
            directory = context.outputDirectory,
            selectionReasonRef = selectionReason.toSerializable(context),
            moduleVersionRef = moduleVersion?.toSerializable(context),
            variantRefs = variants.map { it.toSerializable(context) }
        )

        else -> SerializableResolvedComponentResult.New(
            idRef = id.toSerializable(context),
            selectionReasonRef = selectionReason.toSerializable(context),
            moduleVersionRef = moduleVersion?.toSerializable(context),
            directory = context.outputDirectory,
            variantRefs = variants.map { it.toSerializable(context) },
            dependencyRefs = dependencies.mapNotNull { it.toSerializable(context, visited + id.displayName) }
        )
    }
    val hashCode = resolvedComponentResult.hashCode()
    context.getFile(hashCode, SerializableResolvedComponentResult.DIR_NAME)
        .writeText(context.json.encodeToString(resolvedComponentResult))
    return hashCode
}

private fun ResolvedVariantResult.toSerializable(context: EncodingContext): Int {
    val variantResult = SerializableResolvedVariantResult(
        directory = context.outputDirectory,
        displayName = displayName,
        attributesRef = attributes.toMap(context),
        capabilityRefs = kotlin.runCatching { capabilities.map { it.toSerializable(context) } }
            .getOrDefault(emptyList())
    )
    val hashCode = variantResult.hashCode()
    context.getFile(hashCode, SerializableResolvedVariantResult.DIR_NAME)
        .writeText(context.json.encodeToString(variantResult))
    return hashCode
}

private fun Capability.toSerializable(context: EncodingContext): Int {
    val capability = SerializableCapability(
        group = group,
        name = name,
        version = version
    )
    val hashCode = capability.hashCode()
    context.getFile(hashCode, SerializableCapability.DIR_NAME)
        .writeText(context.json.encodeToString(capability))
    return hashCode
}

private fun AttributeContainer.toMap(context: EncodingContext): Int {
    val map = buildMap {
        for (key in keySet()) {
            this[key.name] = getAttribute(key).toString()
        }
    }
    val hashCode = map.hashCode()
    context.getFile(hashCode, ATTRIBUTES_DIR_NAME)
        .writeText(context.json.encodeToString(map))
    return hashCode
}


private fun ModuleVersionIdentifier.toSerializable(context: EncodingContext): Int {
    val moduleVersionIdentifier = SerializableModuleIdentifier(
        group = group,
        module = name
    )
    val hashCode = moduleVersionIdentifier.hashCode()
    context.getFile(hashCode, SerializableModuleIdentifier.DIR_NAME)
        .writeText(context.json.encodeToString(moduleVersionIdentifier))
    return hashCode
}


private fun ComponentSelectionReason.toSerializable(context: EncodingContext): Int {
    val selectorReason = SerializableComponentSelectorReason(
        isForced = isForced,
        isConflictResolution = isConflictResolution,
        isSelectedByRule = isSelectedByRule,
        isExpected = isExpected,
        isCompositeSubstitution = kotlin.runCatching { isCompositeSubstitution }.getOrDefault(false),
        isConstrained = kotlin.runCatching { isConstrained }.getOrDefault(false),
        descriptionRefs = kotlin.runCatching { this.descriptions.map { it.toSerializable(context) } }
            .getOrDefault(emptyList()),
        directory = context.outputDirectory
    )
    val hashCode = selectorReason.hashCode()
    context.getFile(hashCode, SerializableComponentSelectorReason.DIR_NAME)
        .writeText(context.json.encodeToString(selectorReason))
    return hashCode
}


private fun ComponentSelectionDescriptor.toSerializable(context: EncodingContext): Int {
    val descriptor = SerializableComponentSelectionDescriptor(
        cause = cause.toSerializable(),
        description = description,
        directory = context.outputDirectory
    )
    val hashCode = descriptor.hashCode()
    context.getFile(hashCode, SerializableComponentSelectionDescriptor.DIR_NAME)
        .writeText(context.json.encodeToString(descriptor))
    return hashCode
}

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


private fun ComponentIdentifier.toSerializable(context: EncodingContext): Int {
    val componentIdentifier = when (this) {
        is ProjectComponentIdentifier -> SerializableComponentIdentifier.Project(
            displayName = displayName,
            build = build.toSerializable(),
            projectPath = projectPath
        )

        is ModuleComponentIdentifier -> SerializableComponentIdentifier.Module(
            group = group,
            module = module,
            version = version
        )

        is LibraryBinaryIdentifier -> SerializableComponentIdentifier.LibraryBinary(
            displayName = displayName,
            projectPath = projectPath,
            libraryName = libraryName,
            variant = variant
        )

        else -> SerializableComponentIdentifier.Other(displayName)
    }
    val hashCode = componentIdentifier.hashCode()
    context.getFile(hashCode, SerializableComponentIdentifier.DIR_NAME)
        .writeText(context.json.encodeToString(componentIdentifier))
    return hashCode
}

private fun BuildIdentifier.toSerializable() =
    SerializableBuildIdentifier(
        buildPath = kotlin.runCatching { buildPath }.getOrNull(),
        name = name
    )

private fun ModuleIdentifier.toSerializable() =
    SerializableModuleIdentifier(
        group = group,
        module = name
    )

