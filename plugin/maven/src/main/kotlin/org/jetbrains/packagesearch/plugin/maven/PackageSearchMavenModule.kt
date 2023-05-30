@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.maven

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import org.jetbrains.packagesearch.plugin.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.data.WithIcon
import org.jetbrains.packagesearch.plugin.data.WithIcon.PathSourceType.ClasspathResources

@Serializable
@SerialName("maven")
data class PackageSearchMavenModule(
    override val name: String,
    override val projectDirPath: String,
    override val buildFilePath: String,
    override val declaredKnownRepositories: Map<String, ApiRepository>,
    override val declaredDependencies: List<PackageSearchDeclaredDependency>,
    override val defaultScope: String? = null,
    override val compatiblePackageTypes: List<PackagesType>
) : PackageSearchModule.Base {
    override val icon
        get() = ClasspathResources("icons/maven.svg")
}