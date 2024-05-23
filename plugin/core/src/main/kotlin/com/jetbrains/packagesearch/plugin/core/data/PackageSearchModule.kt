@file:Suppress("FunctionName", "unused")

package com.jetbrains.packagesearch.plugin.core.data

import com.jetbrains.packagesearch.plugin.core.utils.SerializablePath
import java.nio.file.Path
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.search.PackagesType


@Serializable
sealed interface PackageSearchModule : IconProvider, PackageSearchModuleEditor {

    val name: String
    val identity: Identity
    val buildFilePath: Path?
    val declaredRepositories: List<PackageSearchDeclaredRepository>

    val compatiblePackageTypes: List<PackagesType>
    val dependencyMustHaveAScope: Boolean

    interface WithVariants : PackageSearchModule {

        @Serializable
        data class Terminology(val singular: String, val plural: String) {
            fun getForCardinality(count: Int) = if (count > 1) plural else singular

            companion object {
                val DEFAULT = Terminology("variant", "variants")
            }
        }

        val variants: Map<String, PackageSearchModuleVariant>
        val variantTerminology: Terminology

        val mainVariantName: String
    }

    interface Base : PackageSearchModule, PackageSearchDependencyManager {
        val declaredDependencies: List<PackageSearchDeclaredPackage>
        val availableScopes: List<String>
        val defaultScope: String?
    }

    @Serializable
    data class Identity(
        val group: String,
        val path: String,
        val projectDir: SerializablePath,
    )
}
