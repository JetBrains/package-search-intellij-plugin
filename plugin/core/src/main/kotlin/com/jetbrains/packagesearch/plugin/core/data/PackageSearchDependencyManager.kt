package com.jetbrains.packagesearch.plugin.core.data

import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository

interface PackageSearchDependencyManager {

    fun updateDependency(
        context: EditModuleContext,
        declaredPackage: PackageSearchDeclaredPackage,
        newVersion: String?,
        newScope: String?
    )

    fun addDependency(
        context: EditModuleContext,
        apiPackage: ApiPackage,
        selectedVersion: String,
        selectedScope: String?,
    )

    fun removeDependency(
        context: EditModuleContext,
        declaredPackage: PackageSearchDeclaredPackage
    )

}

interface EditModuleContext {
    val data: Any?
}

interface PackageSearchModuleEditor {
    suspend fun editModule(action: EditModuleContext.() -> Unit)

    fun addRepository(
        context: EditModuleContext,
        repository: ApiRepository
    )

    fun removeRepository(
        context: EditModuleContext,
        repository: PackageSearchDeclaredRepository
    )
}