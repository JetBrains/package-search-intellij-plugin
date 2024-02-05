package com.jetbrains.packagesearch.plugin.tests

import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

@Serializable
data class SerializablePackageSearchDeclaredPackage(
    val id: String,
    val displayName: String,
    val coordinates: String,
    val declaredVersion: NormalizedVersion?,
    val remoteInfo: ApiPackage?,
    val declarationIndexes: DependencyDeclarationIndexes,
    val declaredScope: String?,
)