@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.module.Module
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.gradle.utils.getDeclaredDependencies
import com.jetbrains.packagesearch.plugin.gradle.utils.getDeclaredKnownRepositories
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.search.androidPackages
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.jvmGradlePackages

class GradleModuleProvider : AbstractGradleModuleProvider() {

    context(PackageSearchModuleBuilderContext)
    override suspend fun FlowCollector<PackageSearchModule?>.transform(
        module: Module,
        model: PackageSearchGradleModel,
    ) {
        if (!model.isKotlinMultiplatformApplied) {
            val availableKnownRepositories =
                model.repositories.toSet().let { availableGradleRepositories ->
                    knownRepositories.filterValues {
                        it is ApiMavenRepository && it.alternateUrls.intersect(availableGradleRepositories).isNotEmpty()
                    }
                }

            val configurationNames = model.configurations
                .filter { it.canBeDeclared }
                .map { it.name }
            val declaredDependencies = module.getDeclaredDependencies()
            val packageSearchGradleModule = PackageSearchGradleModule(
                name = model.projectName,
                identity = PackageSearchModule.Identity(
                    group = "gradle",
                    path = model.projectIdentityPath
                ),
                buildFilePath = model.buildFilePath,
                declaredKnownRepositories = module.getDeclaredKnownRepositories(),
                declaredDependencies = declaredDependencies,
                availableKnownRepositories = availableKnownRepositories,
                packageSearchModel = model,
                defaultScope = "implementation".takeIf { it in configurationNames } ?: configurationNames.firstOrNull(),
                availableScopes = configurationNames,
                compatiblePackageTypes = buildPackageTypes {
                    mavenPackages()
                    when {
                        model.isKotlinAndroidApplied -> androidPackages()
                        model.isJavaApplied -> jvmGradlePackages("jar")
                        else -> gradlePackages {
                            isRootPublication = true
                        }
                    }
                },
                nativeModule = module,
            )
            emit(packageSearchGradleModule)
        }
    }


}