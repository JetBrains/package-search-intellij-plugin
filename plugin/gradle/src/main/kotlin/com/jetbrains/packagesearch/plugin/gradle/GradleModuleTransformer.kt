@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.module.Module
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleProvider
import com.jetbrains.packagesearch.plugin.core.utils.mapUnit
import com.jetbrains.packagesearch.plugin.core.utils.smartModeFlow
import com.jetbrains.packagesearch.plugin.gradle.utils.getGradleModelRepository
import com.jetbrains.packagesearch.plugin.gradle.utils.getModuleChangesFlow
import com.jetbrains.packagesearch.plugin.gradle.utils.gradleIdentityPathOrNull
import com.jetbrains.packagesearch.plugin.gradle.utils.gradleModel
import com.jetbrains.packagesearch.plugin.gradle.utils.gradleSyncNotifierFlow
import com.jetbrains.packagesearch.plugin.gradle.utils.isGradleSourceSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import com.intellij.openapi.module.Module as NativeModule

@Deprecated(
    "Use BaseGradleModuleProvider instead",
    ReplaceWith(
        "AbstractGradleModuleProvider",
        "com.jetbrains.packagesearch.plugin.gradle.BaseGradleModuleProvider"
    )
)
typealias BaseGradleModuleProvider = AbstractGradleModuleProvider

abstract class AbstractGradleModuleProvider : PackageSearchModuleProvider {

    context(PackageSearchModuleBuilderContext)
    override fun provideModule(
        nativeModule: NativeModule,
    ): Flow<PackageSearchModule?> = when {
        nativeModule.isGradleSourceSet -> flowOf(null)
        else -> merge(
            project.smartModeFlow.mapUnit(),
            project.gradleSyncNotifierFlow,
        )
            .mapNotNull { nativeModule.gradleIdentityPathOrNull }
            .flatMapLatest { gradleIdentityPath ->
                getGradleModelRepository()
                    .changes()
                    .flatMapLatest { it.changedItems }
                    .map { it.item.data }
                    .filter { it.projectIdentityPath == gradleIdentityPath }
                    .onStart {
                        getGradleModelRepository()
                            .gradleModel(forIdentityPath = gradleIdentityPath)
                            ?.let { emit(it) }
                    }
            }
            .flatMapLatest { model ->
                getModuleChangesFlow(model)
                    .map { model }
                    .onStart { emit(model) }
            }
            .transformLatest { model ->
                transform(nativeModule, model)
            }
    }

    context(PackageSearchModuleBuilderContext)
    abstract suspend fun FlowCollector<PackageSearchModule?>.transform(
        module: Module,
        model: PackageSearchGradleModel,
    )
}