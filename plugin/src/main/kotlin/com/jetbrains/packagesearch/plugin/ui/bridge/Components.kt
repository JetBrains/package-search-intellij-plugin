package com.jetbrains.packagesearch.plugin.ui.bridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.intellij.icons.AllIcons
import com.jetbrains.packagesearch.plugin.ui.PackageSearchMetrics
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.DropdownColors
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.theme.dropdownStyle

@Composable
fun LabelInfo(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    val textColor = remember(JewelTheme.isDark) { pickComposeColorFromLaf("TextField.inactiveForeground") }
    Text(
        text = text,
        modifier = modifier,
        color = textColor,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}


@Composable
fun TextSelectionDropdown(
    items: List<String>,
    content: String,
    enabled: Boolean,
    onSelection: (String) -> Unit,
) {
    Dropdown(
        menuModifier = Modifier.heightIn(max = PackageSearchMetrics.Dropdown.maxHeight),
        enabled = enabled && items.isNotEmpty(),
        style = packageSearchDropdownStyle(),
        menuContent = {
            items.forEach {
                selectableItem(
                    selected = false,
                    onClick = { onSelection(it) }
                ) {
                    Text(text = it, maxLines = 1)
                }
            }
        },
        content = {
            Row(horizontalArrangement = Arrangement.End) {
                Text(
                    text = content,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.End
                )
            }
        }
    )
}

@Composable
internal fun PackageActionPopup(
    isOpen: Boolean,
    iconResource: String = "actions/more.svg",
    clazz: Class<AllIcons> = AllIcons::class.java,
    onOpenPopupRequest: () -> Unit,
    onDismissRequest: () -> Unit,
    content: MenuScope.() -> Unit,
) {
    Box {
        IconButton(
            onClick = { onOpenPopupRequest() },
        ) {
            Icon(
                resource = iconResource,
                contentDescription = "more",
                iconClass = clazz
            )
        }
        if (isOpen) {
            PopupMenu(
                horizontalAlignment = Alignment.Start,
                onDismissRequest = { _ ->
                    onDismissRequest()
                    true
                },
                modifier = Modifier.heightIn(
                    min = PackageSearchMetrics.Popups.minHeight,
                    max = PackageSearchMetrics.Popups.maxHeight
                ),
                content = content
            )
        }
    }
}

@Composable
private fun packageSearchDropdownStyle(): DropdownStyle {
    val currentStyle = JewelTheme.dropdownStyle
    return DropdownStyle(
        colors = DropdownColors(
            background = Color.Transparent,
            backgroundDisabled = Color.Transparent,
            backgroundFocused = Color.Transparent,
            backgroundPressed = Color.Transparent,
            backgroundHovered = Color.Transparent,
            content = currentStyle.colors.content,
            contentDisabled = currentStyle.colors.contentDisabled,
            contentFocused = currentStyle.colors.contentFocused,
            contentPressed = currentStyle.colors.contentPressed,
            contentHovered = currentStyle.colors.contentHovered,
            border = Color.Transparent,
            borderDisabled = Color.Transparent,
            borderFocused = Color.Transparent,
            borderPressed = Color.Transparent,
            borderHovered = Color.Transparent,
            iconTintDisabled = Color.Transparent,
            iconTint = currentStyle.colors.iconTint,
            iconTintFocused = currentStyle.colors.iconTintFocused,
            iconTintPressed = currentStyle.colors.iconTintPressed,
            iconTintHovered = currentStyle.colors.iconTintHovered,
        ),
        metrics = currentStyle.metrics,
        icons = currentStyle.icons,
        textStyle = currentStyle.textStyle,
        menuStyle = currentStyle.menuStyle,
    )
}