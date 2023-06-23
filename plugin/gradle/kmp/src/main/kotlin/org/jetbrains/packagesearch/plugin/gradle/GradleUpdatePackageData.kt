@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.plugin.core.data.InstallPackageData
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import org.jetbrains.packagesearch.plugin.core.data.RemovePackageData
import org.jetbrains.packagesearch.plugin.core.data.UpdatePackageData
import org.jetbrains.packagesearch.plugin.core.data.WithIcon.Icons

data class KotlinMultiplatformUpdatePackageData(
    override val installedPackage: PackageSearchKotlinMultiplatformDeclaredPackage,
    override val newVersion: String? = null,
    val configuration: String? = null
) : UpdatePackageData

data class KotlinMultiplatformInstallPackageData(
    override val apiPackage: ApiPackage,
    override val selectedVersion: String,
    val configuration: String? = null
) : InstallPackageData

data class KotlinMultiplatformRemovePackageData(
    override val declaredPackage: PackageSearchKotlinMultiplatformDeclaredPackage,
    val configuration: String
) : RemovePackageData

@Serializable
@SerialName("kotlinMultiplatform")
data class PackageSearchKotlinMultiplatformModule(
    override val name: String,
    override val projectDirPath: String,
    override val buildFilePath: String?,
    override val declaredKnownRepositories: Map<String, ApiRepository>,
    override val defaultScope: String?,
    override val availableScopes: List<String>,
    override val variants: List<PackageSearchModuleVariant>,
    val packageSearchModel: PackageSearchGradleModel,
    val availableKnownRepositories: Map<String, ApiRepository>
) : PackageSearchModule.WithVariants {
    override val icon
        get() = Icons.GRADLE

    val defaultConfiguration
        get() = defaultScope

}
