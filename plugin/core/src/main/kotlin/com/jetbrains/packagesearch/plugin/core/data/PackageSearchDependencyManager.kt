package com.jetbrains.packagesearch.plugin.core.data

import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository

interface PackageSearchDependencyManager {

    context(EditModuleContext)
    fun updateDependency(
        declaredPackage: PackageSearchDeclaredPackage,
        newVersion: String?,
        newScope: String?
    )

    context(EditModuleContext)
    fun addDependency(apiPackage: ApiPackage, selectedVersion: String, selectedScope: String?)

    context(EditModuleContext)
    fun removeDependency(declaredPackage: PackageSearchDeclaredPackage)

}

interface EditModuleContext {
    val data: Any
}

interface PackageSearchModuleEditor {
    suspend fun editModule(action: context(EditModuleContext) () -> Unit)

    context(EditModuleContext) fun addRepository(repository: ApiRepository)
    context(EditModuleContext)  fun removeRepository(repository: PackageSearchDeclaredRepository)
}