@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import org.jetbrains.packagesearch.plugin.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.data.WithIcon.PathSourceType.ClasspathResources

@Serializable
@SerialName("gradle")
data class PackageSearchGradleModule(
    override val name: String,
    override val projectDirPath: String,
    override val buildFilePath: String?,
    override val declaredKnownRepositories: Map<String, ApiRepository>,
    override val declaredDependencies: List<PackageSearchDeclaredDependency>,
    override val defaultScope: String = "implementation",
    override val compatiblePackageTypes: List<PackagesType>,
    val packageSearchModel: PackageSearchGradleModel,
    val availableKnownRepositories: Map<String, ApiRepository>
) : PackageSearchModule.Base {
    override val icon
        get() = ClasspathResources("icons/gradle.svg")

    val defaultConfiguration
        get() = defaultScope
}
