@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.plugin.core.data.InstallPackageData
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.data.RemovePackageData
import org.jetbrains.packagesearch.plugin.core.data.UpdatePackageData
import org.jetbrains.packagesearch.plugin.core.data.WithIcon.Icons

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
    override val identityPath: String,
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

    val defaultConfiguration
        get() = defaultScope

}
