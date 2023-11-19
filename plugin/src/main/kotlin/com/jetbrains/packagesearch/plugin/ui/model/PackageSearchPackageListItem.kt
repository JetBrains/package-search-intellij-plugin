package com.jetbrains.packagesearch.plugin.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import org.jetbrains.jewel.ui.component.MenuScope

typealias Content = @Composable () -> Unit

val EmptyContent: Content = {}

@Stable
sealed interface PackageSearchPackageListItem {
    fun uniqueId(): String

    @Stable
    data class Header(
        val title: String,
        val count: Int,
        val groupId: PackageGroup.Id,
        val badges: List<String>? = null,
        val infoBoxDetail: InfoBoxDetail.Badges? = null,
        val compatibleVariantsText: String? = null,
        val actionContent: Content? = null,
    ) : PackageSearchPackageListItem {
        override fun uniqueId(): String = groupId.value

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Header

            if (groupId != other.groupId) return false
            if (title != other.title) return false
            return true
        }

        override fun hashCode(): Int {
            var result = title.hashCode()
            result = 31 * result + groupId.hashCode()
            return result
        }
    }

    @Stable
    data class Package(
        val icon: IconProvider.Icon,
        val title: String,
        val id: String,
        val groupId: String,
        val subtitle: String? = null,
        val editPackageContent: Content = EmptyContent,
        val mainActionContent: Content = EmptyContent,
        val popupContent: (MenuScope.()->Unit)? = null,
        val infoBoxDetail: InfoBoxDetail.Package,
    ) : PackageSearchPackageListItem {
        override fun uniqueId(): String = "$groupId.$id"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Package

            return id == other.id
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }

}