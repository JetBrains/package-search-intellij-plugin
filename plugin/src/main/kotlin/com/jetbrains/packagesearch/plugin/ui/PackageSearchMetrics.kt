package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

object PackageSearchMetrics {
    val searchBarheight = 36.dp
    val treeActionsHeight = searchBarheight

    object PackagesList {
        object Header {
            //todo discuss about the right behaviour
            fun paddingFor(
                isFirstItem: Boolean,
                isGroupOpen: Boolean,
                isGroupEmpty: Boolean,
                isLastItem: Boolean,
            ): PaddingValues {
                val top = if (!isFirstItem) 2.dp else 1.dp
                val bottom = 0.dp
                return PaddingValues(top = top, bottom = bottom)
            }
        }

        object Package {
            fun paddingFor(isFirstItem: Boolean, isLastItem: Boolean): PaddingValues {
                val top = if (!isFirstItem) 4.dp else 0.dp
                val bottom = if (isLastItem) 2.dp else 0.dp
                return PaddingValues(top = top, bottom = bottom)
            }
        }
    }

}