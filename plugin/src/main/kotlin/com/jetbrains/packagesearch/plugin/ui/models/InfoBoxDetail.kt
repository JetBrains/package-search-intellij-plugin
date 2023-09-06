package com.jetbrains.packagesearch.plugin.ui.models

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import org.jetbrains.packagesearch.api.v3.ApiPackage

sealed interface InfoBoxDetail {

    @JvmInline
    value class RemotePackage(val apiPackage: ApiPackage) : InfoBoxDetail

    @JvmInline
    value class DeclaredPackage(val declaredDependency: PackageSearchDeclaredPackage) : InfoBoxDetail

    @JvmInline
    value class VariantDetails(val variant: PackageSearchModuleVariant) : InfoBoxDetail

    @JvmInline
    value class SearchDetails(val group: PackageGroup.Remote.FromVariants) : InfoBoxDetail
}

fun ApiPackage.asPackageSearchTableItem() =
    InfoBoxDetail.RemotePackage(this)

fun PackageSearchDeclaredPackage.asPackageSearchTableItem() =
    InfoBoxDetail.DeclaredPackage(this)