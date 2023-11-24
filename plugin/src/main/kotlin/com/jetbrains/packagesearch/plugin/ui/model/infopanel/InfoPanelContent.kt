package com.jetbrains.packagesearch.plugin.ui.model.infopanel

import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItem

sealed interface InfoPanelContent {

    val tabTitle: String

    sealed interface PackageInfo : InfoPanelContent {

        data class License(val name: String, val url: String?)

        sealed interface Scm{
            val name: String
            val url: String

            data class GitHub(
                override val name: String,
                override val url: String,
                val stars: Int,
            ) : Scm
        }
        data class Repository(val name: String, val url: String)

        val packageListId: PackageListItem.Package.Id
        val moduleId: PackageSearchModule.Identity
        val title: String
        val subtitle: String
        val icon: IconProvider.Icon
        val type: String?
        val licenses: List<License>
        val authors: List<String>
        val description: String?
        val scm: Scm?
        val readmeUrl: String?
        val repositories: List<Repository>
        val isLoading: Boolean

        sealed interface Declared : PackageInfo {

            override val packageListId: PackageListItem.Package.Declared.Id
            val allowMissingScope: Boolean
            val latestVersion: String?
            val declaredVersion: String
            val availableVersions: List<String>
            val declaredScope: String
            val availableScopes: List<String>

            data class Base(
                override val packageListId: PackageListItem.Package.Declared.Id.Base,
                override val tabTitle: String,
                override val moduleId: PackageSearchModule.Identity,
                override val title: String,
                override val subtitle: String,
                override val icon: IconProvider.Icon,
                override val type: String,
                override val licenses: List<License>,
                override val authors: List<String>,
                override val description: String?,
                override val scm: Scm?,
                override val readmeUrl: String?,
                override val repositories: List<Repository>,
                override val isLoading: Boolean,
                override val latestVersion: String?,
                override val declaredVersion: String,
                override val availableVersions: List<String>,
                override val declaredScope: String,
                override val availableScopes: List<String>,
                override val allowMissingScope: Boolean
                ) : Declared

            data class WithVariant(
                override val packageListId: PackageListItem.Package.Declared.Id.WithVariant,
                override val tabTitle: String,
                override val moduleId: PackageSearchModule.Identity,
                override val title: String,
                override val subtitle: String,
                override val icon: IconProvider.Icon,
                override val type: String,
                override val licenses: List<License>,
                override val authors: List<String>,
                override val description: String?,
                override val scm: Scm?,
                override val readmeUrl: String?,
                override val repositories: List<Repository>,
                override val isLoading: Boolean,
                override val latestVersion: String?,
                override val declaredVersion: String,
                override val availableVersions: List<String>,
                override val declaredScope: String,
                override val availableScopes: List<String>,
                override val allowMissingScope: Boolean,
                val declaredVariant: String,
                val compatibleVariants: List<String>,
                val variantTerminology: PackageSearchModule.WithVariants.Terminology
            ) : Declared
        }

        sealed interface Remote : PackageInfo {

            data class Base(
                override val packageListId: PackageListItem.Package.Remote.Base.Id,
                override val tabTitle: String,
                override val moduleId: PackageSearchModule.Identity,
                override val title: String,
                override val subtitle: String,
                override val icon: IconProvider.Icon,
                override val type: String,
                override val licenses: List<License>,
                override val authors: List<String>,
                override val description: String?,
                override val scm: Scm?,
                override val readmeUrl: String?,
                override val repositories: List<Repository>,
                override val isLoading: Boolean,
            ) : Remote

            data class WithVariant(
                override val packageListId: PackageListItem.Package.Remote.WithVariant.Id,
                override val tabTitle: String,
                override val moduleId: PackageSearchModule.Identity,
                override val title: String,
                override val subtitle: String,
                override val icon: IconProvider.Icon,
                override val type: String,
                override val licenses: List<License>,
                override val authors: List<String>,
                override val description: String?,
                override val scm: Scm?,
                override val readmeUrl: String?,
                override val repositories: List<Repository>,
                override val isLoading: Boolean,
                val isInstalledInPrimaryVariant: Boolean,
                val primaryVariant: String,
                val additionalVariants: List<String>,
            ) : Remote
        }
    }

}