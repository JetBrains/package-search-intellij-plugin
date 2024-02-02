package com.jetbrains.packagesearch.plugin.ui.model.packageslist

import kotlinx.serialization.Serializable

@Serializable
sealed interface PackageListItemEvent {

    val eventId: PackageListItem.Id

    @Serializable
    data class SetHeaderState(
        override val eventId: PackageListItem.Header.Id,
        val targetState: TargetState,
    ) : PackageListItemEvent {
        enum class TargetState {
            OPEN, CLOSE
        }
    }

    @Serializable
    sealed interface InfoPanelEvent : PackageListItemEvent {

        @Serializable
        sealed interface OnHeaderAttributesClick : InfoPanelEvent {
            @Serializable
            data class DeclaredHeaderAttributesClick(
                override val eventId: PackageListItem.Header.Id.Declared,
                val variantName: String,
            ) : OnHeaderAttributesClick

            @Serializable
            data class SearchHeaderAttributesClick(
                override val eventId: PackageListItem.Header.Id.Remote,
                val attributesNames: List<String>,
            ) : OnHeaderAttributesClick

        }

        @Serializable
        data class OnHeaderVariantsClick(override val eventId: PackageListItem.Header.Id) : InfoPanelEvent

        @Serializable
        data class OnPackageSelected(override val eventId: PackageListItem.Package.Id) : InfoPanelEvent

        @Serializable
        data class OnPackageDoubleClick(override val eventId: PackageListItem.Id) : InfoPanelEvent

        @Serializable
        data class OnSelectedPackageClick(override val eventId: PackageListItem.Id) : InfoPanelEvent


    }

    @Serializable
    sealed interface EditPackageEvent : PackageListItemEvent {

        override val eventId: PackageListItem.Package.Declared.Id

        @Serializable
        data class SetPackageScope(
            override val eventId: PackageListItem.Package.Declared.Id,
            val scope: String?,
        ) : EditPackageEvent

        @Serializable
        data class SetPackageVersion(
            override val eventId: PackageListItem.Package.Declared.Id,
            val version: String,
        ) : EditPackageEvent

        @Serializable
        data class SetVariant(
            override val eventId: PackageListItem.Package.Declared.Id.WithVariant,
            val selectedVariantName: String,
        ) : EditPackageEvent

    }

    @Serializable
    data class UpdateAllPackages(
        override val eventId: PackageListItem.Header.Id.Declared,
    ) : PackageListItemEvent


    @Serializable
    sealed interface OnPackageAction : PackageListItemEvent {

        @Serializable
        sealed interface Install : OnPackageAction {

            val headerId: PackageListItem.Header.Id.Remote

            @Serializable
            data class Base(
                override val eventId: PackageListItem.Package.Remote.Base.Id,
                override val headerId: PackageListItem.Header.Id.Remote,
            ) : Install

            @Serializable
            data class WithVariant(
                override val eventId: PackageListItem.Package.Id,
                override val headerId: PackageListItem.Header.Id.Remote,
                val selectedVariantName: String,
            ) : Install
        }


        @Serializable
        data class Update(override val eventId: PackageListItem.Package.Declared.Id) : OnPackageAction

        @Serializable
        data class Remove(override val eventId: PackageListItem.Package.Declared.Id) : OnPackageAction

        @Serializable
        data class GoToSource(override val eventId: PackageListItem.Package.Declared.Id) : OnPackageAction

    }

    @Serializable
    data class OnRetryPackageSearch(
        override val eventId: PackageListItem.SearchError.Id,
    ) : PackageListItemEvent
}