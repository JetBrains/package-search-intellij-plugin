package com.jetbrains.packagesearch.plugin.ui.model.packageslist

import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import kotlinx.serialization.Serializable

sealed interface PackageListItem {

    val title: String
    val id: Id

    @Serializable
    sealed interface Id {
        val moduleIdentity: PackageSearchModule.Identity
    }

    data class Header(
        override val title: String,
        override val id: Id,
        val state: State,
        val count: Int? = null,
        val attriutes: List<String> = emptyList(),
        val additionalContent: AdditionalContent? = null,
    ) : PackageListItem {

        sealed interface AdditionalContent {
            data class VariantsText(val text: String) : AdditionalContent
            data class UpdatesAvailableCount(val count: Int) : AdditionalContent
            data object Loading : AdditionalContent
        }

        enum class State {
            OPEN, CLOSED, LOADING
        }

        @Serializable
        sealed interface Id : PackageListItem.Id {

            @Serializable
            sealed interface Declared : Id {
                @Serializable
                data class Base(override val moduleIdentity: PackageSearchModule.Identity) : Declared
                @Serializable
                data class WithVariant(
                    override val moduleIdentity: PackageSearchModule.Identity,
                    val variantName: String,
                ) : Declared

            }

            @Serializable
            sealed interface Remote : Id {
                @Serializable
                data class Base(override val moduleIdentity: PackageSearchModule.Identity) : Remote
                @Serializable
                data class WithVariant(
                    override val moduleIdentity: PackageSearchModule.Identity,
                    val compatibleVariantNames: List<String>,
                ) : Remote
            }

        }
    }

    sealed interface Package : PackageListItem {

        @Serializable
        sealed interface Id : PackageListItem.Id {
            val packageId: String
        }

        val subtitle: String
        val icon: IconProvider.Icon
        val isLoading: Boolean

        data class Declared(
            override val title: String,
            override val id: Id,
            override val subtitle: String,
            override val icon: IconProvider.Icon,
            override val isLoading: Boolean,
            val allowMissingScope: Boolean,
            val latestVersion: String? = null,
            val selectedScope: String?,
            val availableScopes: List<String>,
            val declaredVersion: String?,
            val availableVersions: List<String>,
        ) : Package {

            @Serializable
            sealed interface Id : Package.Id {
                @Serializable
                data class Base(
                    override val moduleIdentity: PackageSearchModule.Identity,
                    override val packageId: String,
                ) : Id

                @Serializable
                data class WithVariant(
                    override val moduleIdentity: PackageSearchModule.Identity,
                    override val packageId: String,
                    val variantName: String,
                ) : Id
            }

        }

        sealed interface Remote : Package {

            @Serializable
            sealed interface Id : Package.Id

            data class Base(
                override val title: String,
                override val id: Id,
                override val subtitle: String,
                override val icon: IconProvider.Icon,
                override val isLoading: Boolean,
            ) : Remote {
                @Serializable
                data class Id(
                    override val moduleIdentity: PackageSearchModule.Identity,
                    override val packageId: String,
                    val headerId: Header.Id.Remote.Base,
                    ) : Remote.Id
            }

            data class WithVariant(
                override val title: String,
                override val id: Id,
                override val subtitle: String,
                override val icon: IconProvider.Icon,
                override val isLoading: Boolean,
                val primaryVariantName: String,
                val isInstalledInPrimaryVariant: Boolean,
                val additionalVariants: List<String>,
            ) : Remote {
                @Serializable
                data class Id(
                    override val moduleIdentity: PackageSearchModule.Identity,
                    override val packageId: String,
                    val headerId: Header.Id.Remote.WithVariant,
                ) : Remote.Id
            }
        }
    }
}