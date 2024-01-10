package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.scrollbarStyle

object PackageSearchMetrics {

    val scrollbarWidth: Dp
        @Composable
        get() {
            val metrics = JewelTheme.scrollbarStyle.metrics
            return metrics.thumbThickness +
                    metrics.trackPadding.calculateEndPadding(LocalLayoutDirection.current)
        }

    object Splitpane {

        val minWidth: Dp = 300.dp
        const val firstSplitterPositionPercentage = .20f

        const val secondSplittePositionPercentage = .80f
    }

    object Popups {

        val minWidth: Dp = 50.dp
        val maxWidth: Dp = 300.dp

        val minHeight: Dp = 50.dp
        val maxHeight: Dp = 250.dp
    }

    object PackageList {
        object Item {
            val height = 24.dp
            val padding = 8.dp
        }
    }

    val searchBarHeight = 36.dp
    val treeActionsHeight = searchBarHeight

    object Dropdown {
        val maxHeight = 100.dp
    }

    object PackagesList {

        val header = PaddingValues(bottom = 1.dp)

        object Package {
            fun paddingFor(isFirstItem: Boolean, isLastItem: Boolean): PaddingValues {
                val top = if (isFirstItem) 4.dp else 0.dp
                val bottom = if (isLastItem) 4.dp else 0.dp
                return PaddingValues(top = top, bottom = bottom)
            }
        }
    }
}
