@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.data.WithIcon.Icons
import org.jetbrains.packagesearch.plugin.core.utils.asMavenApiPackage

@Serializable
@SerialName("gradle")
data class PackageSearchGradleModule(
    override val name: String,
    override val identity: PackageSearchModule.Identity,
    override val buildFilePath: String?,
    override val declaredKnownRepositories: Map<String, ApiRepository>,
    override val declaredDependencies: List<PackageSearchGradleDeclaredPackage>,
    override val defaultScope: String?,
    override val availableScopes: List<String>,
    override val compatiblePackageTypes: List<PackagesType>,
    val packageSearchModel: PackageSearchGradleModel,
    val availableKnownRepositories: Map<String, ApiRepository>
) : PackageSearchModule.Base {
    override val icon
        get() = Icons.GRADLE

    val defaultConfiguration
        get() = defaultScope

    override fun getInstallData(
        apiPackage: ApiPackage,
        selectedVersion: String,
        selectedScope: String?
    ) = GradleInstallPackageData(
        apiPackage = apiPackage.asMavenApiPackage(),
        selectedVersion = selectedVersion,
        selectedConfiguration = selectedScope ?: error("Scope is not selected")
    )
}
