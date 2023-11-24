@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.gradle

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.module.Module
import com.intellij.packageSearch.mppDependencyUpdater.MppDependency
import com.intellij.packageSearch.mppDependencyUpdater.MppModifierAddData
import com.intellij.packageSearch.mppDependencyUpdater.MppModifierRemoveData
import com.intellij.packageSearch.mppDependencyUpdater.MppModifierUpdateData
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext

internal class EditKMPModuleContext(
    override val data: EditKMPModuleContextData
) : EditModuleContext

class EditKMPModuleContextData(
    val modifier: DependencyModifierService,
    val nativeModule: Module
) {
    private val changes = mutableListOf<MppModifierUpdateData>()
    private val install = mutableListOf<MppModifierAddData>()
    private val remove = mutableListOf<MppModifierRemoveData>()

    fun install(
        sourceSet: String,
        descriptor: MppDependency,
    ) {
        install.add(MppModifierAddData(sourceSet, descriptor))
    }

    fun remove(
        sourceSet: String,
        descriptor: MppDependency,
    ) {
        remove.add(MppModifierRemoveData(sourceSet, descriptor))
    }

    fun update(
        sourceSet: String,
        oldDescriptor: MppDependency,
        newDescriptor: MppDependency,
    ) {
        changes.add(
            MppModifierUpdateData(
                sourceSet = sourceSet,
                oldDescriptor = oldDescriptor,
                newDescriptor = newDescriptor
            )
        )
    }

    fun getUpdates() = changes.toList()
    fun getInstalls() = install.toList()
    fun getRemoves() = remove.toList()
}