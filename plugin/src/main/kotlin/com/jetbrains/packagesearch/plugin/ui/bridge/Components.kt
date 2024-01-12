package com.jetbrains.packagesearch.plugin.ui.bridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.jetbrains.packagesearch.plugin.ui.PackageSearchColors
import com.jetbrains.packagesearch.plugin.ui.PackageSearchMetrics
import java.awt.Cursor
import org.jetbrains.compose.splitpane.SplitPaneScope
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.DropdownLink
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text

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
fun PackageSearchDropdownLink(
    modifier: Modifier,
    menuModifier: Modifier,
    items: List<String>,
    content: String,
    enabled: Boolean,
    onSelection: (String) -> Unit,
) {
    DropdownLink(
        modifier = modifier,
        menuModifier = menuModifier.heightIn(max = PackageSearchMetrics.Dropdown.maxHeight),
        enabled = enabled && items.isNotEmpty(),
        style = LocalPackageSearchDropdownLinkStyle.current,
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
        text = content
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


internal fun SplitPaneScope.packageSearchSplitter(
    cursor: PointerIcon = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)),
) {
    splitter {
        visiblePart {
            Divider(orientation = Orientation.Vertical)
        }
        handle {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .markAsHandle()
                    .pointerHoverIcon(cursor),
            )
        }
    }
}


@Composable
internal fun AttributeBadge(text: String, onClick: () -> Unit) {
    val isDark = JewelTheme.isDark
    val background = remember(isDark) {
        PackageSearchColors.Backgrounds.attributeBadge()
    }

    Box(
        modifier = Modifier
            .background(color = background, shape = RoundedCornerShape(12.dp))
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .onClick { onClick() },
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), text = text,
        )
    }

}

//@Preview
//@Composable
//internal fun AttributeBadgePreview() {
//    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
//        IntUiTheme {
//            Box(Modifier.background(LocalGlobalColors.current.paneBackground).padding(16.dp)) {
//                AttributeBadge(text = "Android") {}
//            }
//        }
//        IntUiTheme(true) {
//            Box(Modifier.background(LocalGlobalColors.current.paneBackground).padding(16.dp)) {
//                AttributeBadge(text = "Android") {}
//            }
//        }
//    }
//
//
//}