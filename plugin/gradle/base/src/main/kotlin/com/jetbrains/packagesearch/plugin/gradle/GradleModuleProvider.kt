@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.module.Module
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.gradle.utils.getDeclaredDependencies
import com.jetbrains.packagesearch.plugin.gradle.utils.getDeclaredKnownRepositories
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.javaApi
import org.jetbrains.packagesearch.api.v3.search.javaRuntime
import org.jetbrains.packagesearch.api.v3.search.libraryElements

class GradleModuleProvider : BaseGradleModuleProvider() {

    override suspend fun FlowCollector<PackageSearchModuleData?>.transform(
        module: Module,
        context: PackageSearchModuleBuilderContext,
        model: PackageSearchGradleModel,
    ) {
        if (!model.isKotlinMultiplatformApplied) {
            val availableKnownRepositories =
                model.repositories.toSet().let { availableGradleRepositories ->
                    context.knownRepositories.filterValues {
                        it is ApiMavenRepository && it.alternateUrls.intersect(availableGradleRepositories).isNotEmpty()
                    }
                }

            val configurationNames = model.configurations.map { it.name }
            val declaredDependencies = module.getDeclaredDependencies(context)
            val packageSearchGradleModule = PackageSearchGradleModule(
                name = model.projectName,
                identity = PackageSearchModule.Identity("gradle", model.projectIdentityPath),
                buildFilePath = model.buildFilePath,
                declaredKnownRepositories = module.getDeclaredKnownRepositories(context),
                declaredDependencies = declaredDependencies,
                availableKnownRepositories = availableKnownRepositories,
                packageSearchModel = model,
                defaultScope = "implementation".takeIf { it in configurationNames } ?: configurationNames.firstOrNull(),
                availableScopes = configurationNames,
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
            emit(
                PackageSearchModuleData(
                    module = packageSearchGradleModule,
                    dependencyManager = PackageSearchGradleDependencyManager(model, module)
                )
            )
        }
    }


}