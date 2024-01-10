package com.jetbrains.packagesearch.plugin.ui.model.infopanel

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItem
import org.jetbrains.packagesearch.api.v3.ApiPackage

sealed interface InfoPanelContentEvent {

    sealed interface Package : InfoPanelContentEvent {
        val module: PackageSearchModule

        val packageListId: PackageListItem.Package.Id

        sealed interface Declared : Package {
            val declaredPackage: PackageSearchDeclaredPackage
            override val packageListId: PackageListItem.Package.Declared.Id

            data class Base(
                override val module: PackageSearchModule.Base,
                override val declaredPackage: PackageSearchDeclaredPackage,
                override val packageListId: PackageListItem.Package.Declared.Id.Base,
            ) : Declared

            data class WithVariant(
                override val module: PackageSearchModule.WithVariants,
                override val declaredPackage: PackageSearchDeclaredPackage,
                override val packageListId: PackageListItem.Package.Declared.Id.WithVariant,
                val variantName: String,
            ) : Declared
        }

        sealed interface Remote : Package {
            val apiPackage: ApiPackage
            override val packageListId: PackageListItem.Package.Remote.Id

            data class Base(
                override val module: PackageSearchModule.Base,
                override val apiPackage: ApiPackage,
                override val packageListId: PackageListItem.Package.Remote.Base.Id,
            ) : Remote

            data class WithVariants(
                override val module: PackageSearchModule.WithVariants,
                override val apiPackage: ApiPackage,
                override val packageListId: PackageListItem.Package.Remote.WithVariant.Id,
                val compatibleVariantNames: List<String>,
                val primaryVariantName: String,
            ) : Remote
        }
    }

    sealed interface Attributes : InfoPanelContentEvent {
        val attributes: List<PackageSearchModuleVariant.Attribute>

        data class FromVariant(
            val variantName: String,
            override val attributes: List<PackageSearchModuleVariant.Attribute>,
        ) : Attributes

        data class FromSearch(
            val defaultVariant: String,
            val additionalVariants: List<String>,
            override val attributes: List<PackageSearchModuleVariant.Attribute>,
        ) : Attributes

    }

}