@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.jetbrains.packagesearch.plugin.core.data.IconProvider.Icons
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.utils.asMavenApiPackage
import java.nio.file.Path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiPackageVersion
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType

@Serializable
@SerialName("gradle")
data class PackageSearchGradleModule(
    override val name: String,
    override val identity: PackageSearchModule.Identity,
    override val buildFilePath: Path?,
    override val declaredKnownRepositories: Map<String, ApiRepository>,
    override val declaredDependencies: List<PackageSearchGradleDeclaredPackage>,
    override val defaultScope: String?,
    override val availableScopes: List<String>,
    override val compatiblePackageTypes: List<PackagesType>,
    val packageSearchModel: PackageSearchGradleModel,
    val availableKnownRepositories: Map<String, ApiRepository>,
) : PackageSearchModule.Base {

    override val dependencyMustHaveAScope: Boolean
        get() = true

    override val icon
        get() = Icons.GRADLE

    val defaultConfiguration
        get() = defaultScope

    override fun getInstallData(
        apiPackage: ApiPackage,
        selectedVersion: ApiPackageVersion,
        selectedScope: String?
    ) = GradleInstallPackageData(
        apiPackage = apiPackage.asMavenApiPackage(),
        selectedVersion = selectedVersion,
        selectedConfiguration = selectedScope ?: defaultConfiguration ?: error("Scope is not selected")
    )
}
