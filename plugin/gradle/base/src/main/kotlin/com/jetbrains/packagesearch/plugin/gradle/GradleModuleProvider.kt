@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.module.Module
import com.jetbrains.packagesearch.plugin.core.PackageSearch
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.gradle.tooling.PackageSearchGradleJavaModel
import com.jetbrains.packagesearch.plugin.gradle.utils.getDeclaredDependencies
import com.jetbrains.packagesearch.plugin.gradle.utils.toGradle
import kotlin.io.path.Path
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.search.androidPackages
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.jvmGradlePackages

class GradleModuleProvider : AbstractGradleModuleProvider() {

    override suspend fun FlowCollector<PackageSearchModule?>.transform(
        context: PackageSearchModuleBuilderContext,
        module: Module,
        model: PackageSearchGradleJavaModel,
    ) {
        if (!PackageSearch.isKMPEnabled || !model.isKotlinMultiplatformApplied) {
            val availableKnownRepositories =
                model.declaredRepositories.toSet().let { availableGradleRepositories ->
                    context.knownRepositories.filterValues {
                        it is ApiMavenRepository && it.alternateUrls.intersect(availableGradleRepositories).isNotEmpty()
                    }
                }

            val configurationNames = model.configurations
                .filter { it.isCanBeDeclared }
                .map { it.name }
            val declaredDependencies = model.buildFilePath
                ?.let { module.getDeclaredDependencies(context) }
                ?: emptyList()
            val packageTypes = buildPackageTypes {
                mavenPackages()
                when {
                    model.isKotlinAndroidApplied -> androidPackages()
                    model.isJavaApplied -> jvmGradlePackages("jar")
                    else -> gradlePackages {
                        isRootPublication = true
                    }
                }
            }
            val identity = PackageSearchModule.Identity(
                group = "gradle",
                path = model.projectIdentityPath.fixBuildSrc(model),
                projectDir = Path(model.projectDir),
            )
            val buildFilePath = model.buildFilePath?.let { Path(it) }
            val declaredRepositories = model.declaredRepositories.toGradle(context)
            val defaultScope = "implementation".takeIf { it in configurationNames } ?: configurationNames.firstOrNull()
            val projectName = model.projectName
            val packageSearchGradleModule: PackageSearchGradleModule = PackageSearchGradleModule(
                name = projectName,
                identity = identity,
                buildFilePath = buildFilePath,
                declaredRepositories = declaredRepositories,
                declaredDependencies = declaredDependencies,
                availableKnownRepositories = availableKnownRepositories,
                packageSearchModel = model,
                defaultScope = defaultScope,
                availableScopes = configurationNames,
                compatiblePackageTypes = packageTypes,
                nativeModule = module,
            )
            emit(packageSearchGradleModule)
        }
    }


}

private fun String.fixBuildSrc(model: PackageSearchGradleJavaModel) = when {
    model.projectName == "buildSrc" && this == ":" -> ":buildSrc"
    else -> this
}
