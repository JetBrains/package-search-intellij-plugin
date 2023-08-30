@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.jetbrains.packagesearch.plugin.core.data.*
import com.jetbrains.packagesearch.plugin.core.data.WithIcon.Icons
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchKotlinMultiplatformVariant.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
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
    override val selectedVersion: String,
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
    override val buildFilePath: String?,
    override val declaredKnownRepositories: Map<String, ApiRepository>,
    override val defaultScope: String?,
    override val availableScopes: List<String>,
    override val variants: Map<String, PackageSearchKotlinMultiplatformVariant>,
    val packageSearchModel: PackageSearchGradleModel,
    val availableKnownRepositories: Map<String, ApiRepository>
) : PackageSearchModule.WithVariants {
    override val icon
        get() = Icons.GRADLE

    override val compatiblePackageTypes: List<PackagesType>
        get() = variants.commonMain.compatiblePackageTypes

    val defaultConfiguration
        get() = defaultScope

    override val mainVariant: PackageSearchModuleVariant
        get() = variants.commonMain

}

val Map<String, PackageSearchKotlinMultiplatformVariant>.commonMain
    get() = getValue("commonMain") as SourceSet

val Map<String, PackageSearchKotlinMultiplatformVariant>.dependenciesBlock: DependenciesBlock
    get() = getValue(DependenciesBlock.name) as DependenciesBlock

val Map<String, PackageSearchKotlinMultiplatformVariant>.cocoapods: Cocoapods
    get() = getValue(Cocoapods.name) as Cocoapods