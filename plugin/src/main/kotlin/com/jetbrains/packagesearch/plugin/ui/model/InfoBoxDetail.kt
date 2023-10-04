package com.jetbrains.packagesearch.plugin.ui.model

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import org.jetbrains.packagesearch.api.v3.ApiPackage

sealed interface InfoBoxDetail {

    sealed interface Package : InfoBoxDetail {
        @JvmInline
        value class RemotePackage(val apiPackage: ApiPackage) : Package

        data class DeclaredPackage(
            val declaredDependency: PackageSearchDeclaredPackage,
            val module: PackageSearchModule
        ) : Package
    }

    sealed interface Badges : InfoBoxDetail {
        @JvmInline
        value class Variant(val variant: PackageSearchModuleVariant) : Badges

        @JvmInline
        value class Search(val group: PackageGroup.Remote.FromVariants) : Badges
    }


}