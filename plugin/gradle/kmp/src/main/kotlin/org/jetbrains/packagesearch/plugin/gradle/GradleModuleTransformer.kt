@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.module.Module
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.search.buildPackagesType
import org.jetbrains.packagesearch.api.v3.search.javaApi
import org.jetbrains.packagesearch.api.v3.search.javaRuntime
import org.jetbrains.packagesearch.api.v3.search.libraryElements
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension

class GradleModuleTransformer : BaseGradleModuleTransformer() {

    override fun PolymorphicModuleBuilder<PackageSearchModule>.registerModuleSerializer() {
        subclass(PackageSearchGradleModule.serializer())
    }

    override fun PolymorphicModuleBuilder<PackageSearchDeclaredPackage>.registerVersionSerializer() {
        subclass(PackageSearchGradleDeclaredPackage.serializer())
    }

    override suspend fun Module.transform(
        context: PackageSearchModuleBuilderContext,
        model: PackageSearchGradleModel,
        buildFile: Path?,
    ): PackageSearchGradleModule {
        val availableKnownRepositories =
            model.repositories.toSet().let { availableGradleRepositories ->
                context.knownRepositories.filterValues {
                    it is ApiMavenRepository && it.alternateUrls.intersect(availableGradleRepositories).isNotEmpty()
                }
            }

        val isKts = buildFile?.extension == "kts"

        val configurationNames = model.configurations.map { name }
        val declaredDependencies = getDeclaredDependencies(context, isKts)
        val usedConfigurations = declaredDependencies.map { it.configuration }
        return PackageSearchGradleModule(
            name = model.projectName,
            projectDirPath = model.projectDir,
            buildFilePath = buildFile?.absolutePathString(),
            declaredKnownRepositories = getDeclaredKnownRepositories(context),
            declaredDependencies = declaredDependencies,
            availableKnownRepositories = availableKnownRepositories,
            packageSearchModel = model,
            defaultScope = "implementation".takeIf { it in configurationNames },
            availableScopes = commonConfigurations
                .filter { it !in configurationNames }
                .plus(usedConfigurations)
                .distinct(),
            compatiblePackageTypes = buildPackagesType {
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
    }


}
