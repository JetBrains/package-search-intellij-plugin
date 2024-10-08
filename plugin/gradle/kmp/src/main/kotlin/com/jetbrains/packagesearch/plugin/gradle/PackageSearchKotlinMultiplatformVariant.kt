@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.packageSearch.mppDependencyUpdater.MppDependency
import com.intellij.packageSearch.mppDependencyUpdater.resolved.MppCompilationInfoModel
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.core.utils.toUnifiedDependency
import com.jetbrains.packagesearch.plugin.core.utils.validateMavenDeclaredPackageType
import com.jetbrains.packagesearch.plugin.core.utils.validateMavenPackageType
import com.jetbrains.packagesearch.plugin.gradle.PackageSearchKotlinMultiplatformDeclaredDependency.*
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiPackageVersion
import org.jetbrains.packagesearch.api.v3.search.PackagesType

@Serializable
sealed interface PackageSearchKotlinMultiplatformVariant : PackageSearchModuleVariant {

    override val dependencyMustHaveAScope: Boolean
        get() = true

    @Serializable
    data class SourceSet(
        override val name: String,
        override val declaredDependencies: List<PackageSearchKotlinMultiplatformDeclaredDependency>,
        override val attributes: List<PackageSearchModuleVariant.Attribute>,
        override val compatiblePackageTypes: List<PackagesType>,
        val compilerTargets: Set<MppCompilationInfoModel.Compilation>,
    ) : PackageSearchKotlinMultiplatformVariant {

        companion object {
            val TERMINOLOGY = PackageSearchModule.WithVariants.Terminology("source set", "source sets")
        }

        override val dependencyMustHaveAScope: Boolean
            get() = true

        override val availableScopes: List<String>
            get() = listOf("implementation", "api", "compileOnly", "runtimeOnly")

        override val defaultScope: String
            get() = "implementation"

        override val isPrimary: Boolean
            get() = name.contains("main", ignoreCase = true)

        override val variantTerminology: PackageSearchModule.WithVariants.Terminology
            get() = TERMINOLOGY

        override fun isCompatible(dependency: ApiPackage, version: ApiPackageVersion): Boolean = when (dependency) {
            is ApiMavenPackage -> when (version) {
                is ApiMavenPackage.MavenVersion -> PackagesType.Maven in compatiblePackageTypes
                is ApiMavenPackage.GradleVersion -> compatiblePackageTypes.filterIsInstance<PackagesType.Gradle>()
                    .takeIf { it.isNotEmpty() }
                    ?.any { compatibleGradlePackageType ->
                        compatibleGradlePackageType.variants.all { requiredVariant ->
                            version.variants.any { availableVariant ->
                                requiredVariant.attributes.all { (attributeName, attribute) ->
                                    availableVariant.attributes[attributeName]
                                        ?.let { attribute.isCompatible(it) } ?: false
                                }
                            }
                        }
                    } ?: false
            }
        }

        override fun updateDependency(
            context: EditModuleContext,
            declaredPackage: PackageSearchDeclaredPackage,
            newVersion: String?,
            newScope: String?,
        ) {
            validateKMPDeclaredPackageType(declaredPackage)
            when (declaredPackage) {
                is PackageSearchKotlinMultiplatformDeclaredDependency.Cocoapods -> TODO()
                is Npm -> TODO()
                is Maven -> {
                    val oldDescriptor = declaredPackage.toMPPDependency()
                    val newDescriptor = oldDescriptor.copy(
                        version = newVersion ?: declaredPackage.declaredVersion.toString(),
                        configuration = newScope ?: declaredPackage.configuration,
                    )
                    context.kmpData.update(
                        sourceSet = name,
                        oldDescriptor = oldDescriptor,
                        newDescriptor = newDescriptor,
                    )

                }
            }
        }

        override fun addDependency(
            context: EditModuleContext,
            apiPackage: ApiPackage,
            selectedVersion: String,
            selectedScope: String?,
        ) {
            context.validate()
            when (apiPackage) {
                is ApiMavenPackage -> {
                    val newDescriptor = MppDependency.Maven(
                        groupId = apiPackage.groupId,
                        artifactId = apiPackage.artifactId,
                        version = selectedVersion,
                        configuration = selectedScope ?: defaultScope
                    )
                    context.kmpData.install(
                        sourceSet = name,
                        descriptor = newDescriptor
                    )
                }
            }
        }

        override fun removeDependency(context: EditModuleContext, declaredPackage: PackageSearchDeclaredPackage) {
            context.validate()
            validateKMPDeclaredPackageType(declaredPackage)
            when (declaredPackage) {
                is PackageSearchKotlinMultiplatformDeclaredDependency.Cocoapods -> TODO()
                is Npm -> TODO()
                is Maven -> {
                    val oldDescriptor = declaredPackage.toMPPDependency()
                    context.kmpData.remove(
                        sourceSet = name,
                        descriptor = oldDescriptor
                    )
                }
            }
        }
    }

    @Serializable
    data class DependenciesBlock(
        override val declaredDependencies: List<Maven>,
        override val compatiblePackageTypes: List<PackagesType>,
        override val availableScopes: List<String>,
        override val defaultScope: String?,
    ) : PackageSearchKotlinMultiplatformVariant {

        override val dependencyMustHaveAScope: Boolean
            get() = true

        override val attributes: List<PackageSearchModuleVariant.Attribute>
            get() = emptyList()

        companion object {
            const val NAME = "dependencies block"
        }

        override val name: String = NAME
        override val variantTerminology = null

        override val isPrimary: Boolean
            get() = true

        override fun isCompatible(dependency: ApiPackage, version: ApiPackageVersion) = when (dependency) {
            is ApiMavenPackage -> true
            else -> false
        }

        override fun updateDependency(
            context: EditModuleContext,
            declaredPackage: PackageSearchDeclaredPackage,
            newVersion: String?,
            newScope: String?,
        ) {
            context.validate()
            validateMavenDeclaredPackageType(declaredPackage)
            val oldDescriptor = declaredPackage.toUnifiedDependency()
            val newDescriptor = oldDescriptor.copy(
                coordinates = oldDescriptor.coordinates.copy(
                    version = newVersion ?: declaredPackage.declaredVersion.toString()
                ),
                scope = newScope ?: declaredPackage.declaredScope ?: defaultScope,
            )
            context.kmpData.modifier.updateDependency(
                module = context.kmpData.nativeModule,
                oldDescriptor = oldDescriptor,
                newDescriptor = newDescriptor
            )
        }

        override fun addDependency(
            context: EditModuleContext,
            apiPackage: ApiPackage,
            selectedVersion: String,
            selectedScope: String?,
        ) {
            context.validate()
            validateMavenPackageType(apiPackage)
            context.kmpData.modifier.addDependency(
                module = context.kmpData.nativeModule,
                descriptor = UnifiedDependency(
                    groupId = apiPackage.groupId,
                    artifactId = apiPackage.artifactId,
                    version = selectedVersion,
                    configuration = selectedScope ?: defaultScope,
                )
            )
        }


        override fun removeDependency(context: EditModuleContext, declaredPackage: PackageSearchDeclaredPackage) {
            context.validate()
            validateMavenDeclaredPackageType(declaredPackage)
            context.kmpData.modifier.removeDependency(
                module = context.kmpData.nativeModule,
                descriptor = declaredPackage.toUnifiedDependency()
            )
        }
    }

    @Serializable
    data class Cocoapods(
        override val declaredDependencies: List<PackageSearchKotlinMultiplatformDeclaredDependency.Cocoapods>,
        override val compatiblePackageTypes: List<PackagesType>,
    ) : PackageSearchKotlinMultiplatformVariant {

        override val isPrimary: Boolean
            get() = false

        override val availableScopes: List<String>
            get() = emptyList()

        override val defaultScope: String?
            get() = null

        override val dependencyMustHaveAScope: Boolean
            get() = false

        override val attributes: List<PackageSearchModuleVariant.Attribute>
            get() = listOf(PackageSearchModuleVariant.Attribute.StringAttribute(NAME))

        companion object {
            const val NAME = "cocoapods"
        }

        override val name: String = NAME
        override val variantTerminology = null

        override fun isCompatible(dependency: ApiPackage, version: ApiPackageVersion) = when (dependency) {
            is ApiMavenPackage -> false
        }


        override fun updateDependency(
            context: EditModuleContext,
            declaredPackage: PackageSearchDeclaredPackage,
            newVersion: String?,
            newScope: String?,
        ) {
            TODO("Not yet implemented")
        }

        override fun addDependency(
            context: EditModuleContext,
            apiPackage: ApiPackage,
            selectedVersion: String,
            selectedScope: String?,
        ) {
            TODO("Not yet implemented")
        }

        override fun removeDependency(
            context: EditModuleContext,
            declaredPackage: PackageSearchDeclaredPackage,
        ) {
            TODO("Not yet implemented")
        }
    }
}