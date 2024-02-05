package com.jetbrains.packagesearch.plugin.tests

import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.util.io.toNioPathOrNull
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.future
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.asPromise

val PKGS_TEST_DATA_OUTPUT_DIR by lazy {
    val dir = System.getenv("PKGS_TEST_DATA_OUTPUT_DIR")?.toNioPathOrNull()
        ?: (appSystemDir / "packagesearch" / "testData")
    dir.createDirectories()
}

internal fun <T> CoroutineScope.promise(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T,
) = future(
    context = context,
    start = start,
    block = block
).asPromise()

fun PackageSearchModule.toSerializable() = when (this) {
    is PackageSearchModule.Base -> SerializablePackageSearchModule.Base(
        name = name,
        identity = identity,
        buildFilePath = buildFilePath?.absolutePathString(),
        declaredKnownRepositories = declaredKnownRepositories,
        compatiblePackageTypes = compatiblePackageTypes,
        dependencyMustHaveAScope = dependencyMustHaveAScope,
        declaredDependencies = declaredDependencies.map { it.toSerializable() },
        availableScopes = availableScopes,
        defaultScope = defaultScope,
    )

    is PackageSearchModule.WithVariants -> SerializablePackageSearchModule.WithVariants(
        name = name,
        identity = identity,
        buildFilePath = buildFilePath?.absolutePathString(),
        declaredKnownRepositories = declaredKnownRepositories,
        compatiblePackageTypes = compatiblePackageTypes,
        dependencyMustHaveAScope = dependencyMustHaveAScope,
        variants = variants.mapValues { it.value.toSerializable() },
        variantTerminology = variantTerminology,
        mainVariantName = mainVariantName,
    )
}

private fun PackageSearchModuleVariant.toSerializable() =
    SerializablePackageSearchModuleVariant(
        name = name,
        variantTerminology = variantTerminology,
        declaredDependencies = declaredDependencies.map { it.toSerializable() },
        attributes = attributes,
        compatiblePackageTypes = compatiblePackageTypes,
        isPrimary = isPrimary,
        dependencyMustHaveAScope = dependencyMustHaveAScope,
        availableScopes = availableScopes,
        defaultScope = defaultScope,
    )

fun PackageSearchDeclaredPackage.toSerializable() =
    SerializablePackageSearchDeclaredPackage(
        id = id,
        displayName = displayName,
        coordinates = coordinates,
        declaredVersion = declaredVersion,
        remoteInfo = remoteInfo,
        declarationIndexes = declarationIndexes,
        declaredScope = declaredScope,
    )

fun Throwable.toSerializable(): SerializableThrowable = SerializableThrowable(
    typeName = this::class.qualifiedName,
    message = message ?: "",
    stackTrace = stackTraceToString().lines(),
    cause = cause?.toSerializable(),
)

inline fun <T, R> Flow<T>.map(
    context: CoroutineContext,
    crossinline transform: suspend (value: T) -> R,
): Flow<R> = map { withContext(context) { transform(it) } }