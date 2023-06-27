@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.module.Module
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.javaApi
import org.jetbrains.packagesearch.api.v3.search.javaRuntime
import org.jetbrains.packagesearch.api.v3.search.libraryElements
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import org.jetbrains.packagesearch.plugin.gradle.utils.generateAvailableScope
import org.jetbrains.packagesearch.plugin.gradle.utils.listOf
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension

class GradleModuleTransformer : BaseGradleModuleTransformer() {

    override suspend fun Module.transform(
        context: PackageSearchModuleBuilderContext,
        model: PackageSearchGradleModel,
        buildFile: Path?,
    ): PackageSearchModuleData {
        val availableKnownRepositories =
            model.repositories.toSet().let { availableGradleRepositories ->
                context.knownRepositories.filterValues {
                    it is ApiMavenRepository && it.alternateUrls.intersect(availableGradleRepositories).isNotEmpty()
                }
            }

        val isKts = buildFile?.extension == "kts"

        val configurationNames = model.configurations.map { name }
        val declaredDependencies = getDeclaredDependencies(context, isKts)
        val availableScopes = generateAvailableScope(declaredDependencies, configurationNames)
        val packageSearchGradleModule = PackageSearchGradleModule(
            name = model.projectName,
            identityPath = listOf(model.rootProjectName, model.projectIdentityPath.split(":").dropWhile { it.isBlank() }),
            buildFilePath = buildFile?.absolutePathString(),
            declaredKnownRepositories = getDeclaredKnownRepositories(context),
            declaredDependencies = declaredDependencies,
            availableKnownRepositories = availableKnownRepositories,
            packageSearchModel = model,
            defaultScope = "implementation".takeIf { it in configurationNames },
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