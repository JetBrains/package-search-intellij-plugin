@file:Suppress("UnstableApiUsage", "DeprecatedCallableAddReplaceWith")

package com.jetbrains.packagesearch.plugin.gradle

import com.jetbrains.packagesearch.plugin.core.data.IconProvider.Icons
import com.jetbrains.packagesearch.plugin.core.data.InstallPackageData
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.core.data.RemovePackageData
import com.jetbrains.packagesearch.plugin.core.data.UpdatePackageData
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchKotlinMultiplatformVariant.Cocoapods
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchKotlinMultiplatformVariant.DependenciesBlock
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchKotlinMultiplatformVariant.SourceSet
import java.nio.file.Path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiPackageVersion
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType

data class KotlinMultiplatformUpdatePackageData(
    override val installedPackage: PackageSearchKotlinMultiplatformDeclaredDependency,
    override val newVersion: String?,
    override val newScope: String?,
    val sourceSetName: String
) : UpdatePackageData

data class KotlinMultiplatformInstallPackageData(
    override val apiPackage: ApiPackage,
    override val selectedVersion: ApiPackageVersion,
    val selectedConfiguration: String,
    val variantName: String
) : InstallPackageData

data class KotlinMultiplatformRemovePackageData(
    override val declaredPackage: PackageSearchKotlinMultiplatformDeclaredDependency,
    val variantName: String
) : RemovePackageData

@Serializable
@SerialName("kotlinMultiplatform")
data class PackageSearchKotlinMultiplatformModule(
    override val name: String,
    override val identity: PackageSearchModule.Identity,
    override val buildFilePath: Path?,
    override val declaredKnownRepositories: Map<String, ApiRepository>,
    override val variants: Map<String, PackageSearchKotlinMultiplatformVariant>,
    val packageSearchModel: PackageSearchGradleModel,
    val availableKnownRepositories: Map<String, ApiRepository>,
) : PackageSearchModule.WithVariants {

    @Deprecated("Use scopes from variants instead")
    override val availableScopes: List<String>
        get() = mainVariant.availableScopes

    @Deprecated("Use scopes from variants instead")
    override val defaultScope: String?
        get() = mainVariant.defaultScope

    override val dependencyMustHaveAScope: Boolean
        get() = true

    override val icon
        get() = Icons.KOTLIN

    override val compatiblePackageTypes: List<PackagesType>
        get() = mainVariant.compatiblePackageTypes

    override val mainVariant: PackageSearchModuleVariant
        get() = variants.commonMain

}

val Map<String, PackageSearchKotlinMultiplatformVariant>.commonMain: SourceSet
    get() = get("commonMain") as SourceSet

val Map<String, PackageSearchKotlinMultiplatformVariant>.dependenciesBlock: DependenciesBlock
    get() = getValue(DependenciesBlock.NAME) as DependenciesBlock

val Map<String, PackageSearchKotlinMultiplatformVariant>.cocoapods: Cocoapods?
    get() = get(Cocoapods.NAME) as Cocoapods?