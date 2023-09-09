@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.module.Module
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.javaApi
import org.jetbrains.packagesearch.api.v3.search.javaRuntime
import org.jetbrains.packagesearch.api.v3.search.libraryElements
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.gradle.utils.generateAvailableScope
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule

class GradleModuleProvider : BaseGradleModuleProvider() {

    override suspend fun Module.transform(
        context: PackageSearchModuleBuilderContext,
        model: PackageSearchGradleModel,
        buildFile: Path?,
    ): PackageSearchModuleData? {
        if (model.isKotlinMultiplatformApplied) return null

        val availableKnownRepositories =
            model.repositories.toSet().let { availableGradleRepositories ->
                context.knownRepositories.filterValues {
                    it is ApiMavenRepository && it.alternateUrls.intersect(availableGradleRepositories).isNotEmpty()
                }
            }

        val configurationNames = model.configurations.map { it.name }
        val declaredDependencies = getDeclaredDependencies(context)
        val availableScopes = generateAvailableScope(declaredDependencies, configurationNames)
        val packageSearchGradleModule = PackageSearchGradleModule(
            name = model.projectName,
            identity = PackageSearchModule.Identity("gradle", model.projectIdentityPath),
            buildFilePath = buildFile?.absolutePathString(),
            declaredKnownRepositories = getDeclaredKnownRepositories(context),
            declaredDependencies = declaredDependencies,
            availableKnownRepositories = availableKnownRepositories,
            packageSearchModel = model,
            defaultScope = "implementation".takeIf { it in configurationNames } ?: configurationNames.firstOrNull(),
            availableScopes = availableScopes,
            compatiblePackageTypes = buildPackageTypes {
                mavenPackages()
                when {
                    model.isKotlinJvmApplied -> gradlePackages {
                        mustBeRootPublication = true
                        variant {
                            javaApi()
                            javaRuntime()
                            libraryElements("jar")
                        }
                    }

                    model.isKotlinAndroidApplied -> {
                        gradlePackages {
                            mustBeRootPublication = true
                            variant {
                                javaApi()
                                javaRuntime()
                                libraryElements("aar")
                            }
                        }
                        gradlePackages {
                            mustBeRootPublication = true
                            variant {
                                javaApi()
                                javaRuntime()
                                libraryElements("jar")
                            }
                        }
                    }

                    else -> gradlePackages {
                        mustBeRootPublication = true
                    }
                }
            }
        )
        return PackageSearchModuleData(
            module = packageSearchGradleModule,
            dependencyManager = PackageSearchGradleDependencyManager(this)
        )
    }


}