package com.jetbrains.packagesearch.plugin.ui.panels.packages

import ai.grazie.utils.mpp.UUID
import androidx.compose.runtime.MutableState
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.packagesearch.plugin.core.data.InstallPackageData
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.ui.ActionState
import com.jetbrains.packagesearch.plugin.ui.ActionType
import com.jetbrains.packagesearch.plugin.ui.panels.packages.items.getLatestVersion
import com.jetbrains.packagesearch.plugin.utils.logDebug
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.packagesearch.api.v3.ApiPackage

internal fun goToSource(
    service: PackageSearchProjectService,
    virtualFile: VirtualFile,
    packageSearchDeclaredPackage: PackageSearchDeclaredPackage,
) {
    service.coroutineScope.launch(Dispatchers.EDT) {
        FileEditorManager
            .getInstance(service.project)
            .openFileEditor(
                OpenFileDescriptor(
                    service.project,
                    virtualFile,
                    packageSearchDeclaredPackage.declarationIndexes.declarationStartIndex
                ),
                true
            )
    }
}

internal fun deleteAction(
    context: PackageSearchProjectService,
    dependencyManager: PackageSearchDependencyManager,
    packageSearchDeclaredPackage: PackageSearchDeclaredPackage,
    isActionPerforming: MutableState<ActionState?>,
    popupOpenStatus: MutableState<String?>,
    service: PackageSearchProjectService,
) {
    val id = UUID.random().text
    isActionPerforming.value = ActionState(true, ActionType.REMOVE, id)
    context.coroutineScope.launch {
        dependencyManager.removeDependency(
            context,
            packageSearchDeclaredPackage.getRemoveData(),
        )
    }.invokeOnCompletion {
        it?.let {
            logDebug("Failed action Remove for package: ${packageSearchDeclaredPackage.id}", it)
        }
        popupOpenStatus.value = null
    }
    service.coroutineScope.launch {
        delay(5.seconds)
        if (isActionPerforming.value?.actionId == id) {
            logDebug("Timeout action Remove for package: ${packageSearchDeclaredPackage.id}")
            isActionPerforming.value = null
        }
    }
}

internal fun addPackageToModule(
    it: PackageSearchModuleVariant,
    apiPackage: ApiPackage,
    onlyStable: Boolean,
    isActionPerforming: MutableState<ActionState?>,
    dependencyManager: PackageSearchDependencyManager,
    onActionComplete: (isSuccess: Boolean) -> Unit,
    service: PackageSearchProjectService,
) {
    val packageInstallData = it.getInstallData(
        apiPackage,
        apiPackage.getLatestVersion(onlyStable)
    )
    addAction(
        isActionPerforming = isActionPerforming,
        dependencyManager = dependencyManager,
        installPackageData = packageInstallData,
        onActionComplete = onActionComplete,
        service = service
    )
}

internal fun addAction(
    isActionPerforming: MutableState<ActionState?>,
    dependencyManager: PackageSearchDependencyManager,
    installPackageData: InstallPackageData,
    onActionComplete: (success: Boolean) -> Unit,
    service: PackageSearchProjectService,
) {
    val id = UUID.random().text
    isActionPerforming.value = ActionState(true, ActionType.ADD, id)
    service.coroutineScope.launch {
        dependencyManager.addDependency(
            service,
            installPackageData,
        )
        onActionComplete(true)
    }.invokeOnCompletion {
        it?.let {
            logDebug("Error while adding dependency:\n$installPackageData", it)
        }
        onActionComplete(false)
    }
    service.coroutineScope.launch {
        delay(5.seconds)
        if (isActionPerforming.value?.actionId == id) {
            logDebug("Add action has been cancelled due a time out")
            onActionComplete(false)
        }
    }
}