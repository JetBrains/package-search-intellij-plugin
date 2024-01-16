package com.jetbrains.packagesearch.plugin.ui.bridge

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.ui.PackageSearchMetrics
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.LocalGlobalColors
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.styling.LazyTreeMetrics
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.LinkColors
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultTabStyle
import org.jetbrains.jewel.ui.component.styling.LocalLazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.TabMetrics
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.theme.linkStyle


@Composable
internal fun PackageSearchDropdownLinkStyle(): LinkStyle {
    val currentStyle = JewelTheme.linkStyle
    val contentColor = LocalContentColor.current
    return LinkStyle(
        colors = LinkColors(
            content = contentColor,
            contentDisabled = currentStyle.colors.contentDisabled,
            contentHovered = contentColor,
            contentPressed = contentColor,
            contentFocused = contentColor,
            contentVisited = contentColor,
        ),
        metrics = currentStyle.metrics,
        icons = currentStyle.icons,
        textStyles = currentStyle.textStyles,
    )
}


@Composable
internal fun PackageSearchTabStyle(): TabStyle {
    val current = LocalDefaultTabStyle.current
    return TabStyle(
        colors = current.colors,
        metrics = TabMetrics(
            underlineThickness = current.metrics.underlineThickness,
            tabPadding = current.metrics.tabPadding,
            tabHeight = PackageSearchMetrics.searchBarHeight,
            closeContentGap = current.metrics.closeContentGap,
            tabContentSpacing = current.metrics.tabContentSpacing,
        ),
        icons = current.icons,
        contentAlpha = current.contentAlpha
    )
}

@Composable
fun PackageSearchGlobalColors(): GlobalColors {
    val colors = LocalGlobalColors.current

    return remember(colors) {
        GlobalColors(
            borders = colors.borders,
            outlines = OutlineColors(
                focused = Color.Transparent,
                focusedWarning = colors.outlines.focusedWarning,
                focusedError = colors.outlines.focusedError,
                warning = colors.outlines.warning,
                error = colors.outlines.error,
            ),
            infoContent = colors.infoContent,
            paneBackground = colors.paneBackground,
        )
    }
}

@Composable
internal fun PackageSearchTreeStyle(): LazyTreeStyle {
    val currentStyle = LocalLazyTreeStyle.current
    val paddings = currentStyle.metrics.elementPadding
    return LazyTreeStyle(
        currentStyle.colors,
        metrics = LazyTreeMetrics(
            indentSize = currentStyle.metrics.indentSize,
            elementPadding = PaddingValues(
                top = paddings.calculateTopPadding(),
                bottom = paddings.calculateBottomPadding(),
                start = paddings.calculateStartPadding(LocalLayoutDirection.current),
                end = 0.dp
            ),
            elementContentPadding = currentStyle.metrics.elementContentPadding,
            elementMinHeight = currentStyle.metrics.elementMinHeight,
            chevronContentGap = currentStyle.metrics.chevronContentGap,
            elementBackgroundCornerSize = currentStyle.metrics.elementBackgroundCornerSize,
        ),
        currentStyle.icons,
    )
}


internal val LocalPackageSearchDropdownLinkStyle: ProvidableCompositionLocal<LinkStyle> =
    staticCompositionLocalOf {
        error("No PackageSearchDropdownLinkStyle provided. Have you forgotten the theme?")
    }
