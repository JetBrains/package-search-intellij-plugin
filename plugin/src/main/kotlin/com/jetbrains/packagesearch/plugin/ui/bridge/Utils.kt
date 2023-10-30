package com.jetbrains.packagesearch.plugin.ui.bridge

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import java.awt.Cursor
import java.awt.Desktop
import java.net.URI
import javax.swing.UIDefaults
import javax.swing.UIManager
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.buildTree

fun java.awt.Color.toComposeColor(): Color {
    return Color(red, green, blue, alpha)
}

fun UIDefaults.getComposeColor(key: String): Color? {
    return getColor(key)?.toComposeColor()
}

fun List<PackageSearchModuleData>.asTree(): Tree<PackageSearchModuleData> {
    val tree= buildTree {
        groupBy { it.module.identity.group }
            .values
            .forEach {
                val sortedItems = it.sortedBy { it.module.identity.path }
                val roots = sortedItems.filter { it.module.identity.path == ":" }.toSet()
                roots.forEach { packageSearchModuleData ->
                    addElements(
                        sortedItems = sortedItems - roots,
                        currentData = packageSearchModuleData,
                        isRoot = true
                    )
                }
            }
    }
    return tree
}

fun TreeGeneratorScope<PackageSearchModuleData>.addElements(
    sortedItems: List<PackageSearchModuleData>,
    currentData: PackageSearchModuleData,
    isRoot: Boolean = false,
) {
    val children = sortedItems
        .filter {
            val toRemove = buildString {
                append(currentData.module.identity.path)
                if (!isRoot) append(":")
            }
            it.module.identity.path.removePrefix(toRemove).run {
                isNotEmpty() && !contains(":")
            }
        }
        .toSet()
    if (children.isNotEmpty()) {
        addNode(
            data = currentData,
            selectionId = currentData.module.identity,
            uiId = ModelUiKey(
                moduleIdentity = currentData.module.identity,
                hasUpdate = currentData.module.hasUpdates,
                hasStableUpdate = currentData.module.hasStableUpdates,
            )
        ) {
            children.forEach {
                addElements(
                    sortedItems = sortedItems - children,
                    currentData = it
                )
            }
        }
    } else {
        addLeaf(
            data = currentData,
            selectionId = currentData.module.identity,
            uiId = ModelUiKey(
                moduleIdentity = currentData.module.identity,
                hasUpdate = currentData.module.hasUpdates,
                hasStableUpdate = currentData.module.hasStableUpdates,
            )
        )
    }
}

internal data class ModelUiKey(
    val moduleIdentity: PackageSearchModule.Identity,
    val hasUpdate: Boolean,
    val hasStableUpdate: Boolean,
)

fun openLinkInBrowser(url: String) {
    Desktop.getDesktop()
        ?.takeIf { it.isSupported(Desktop.Action.BROWSE) }
        ?.browse(URI(url))
}


fun pickComposeColorFromLaf(key: String) =
    UIManager.getLookAndFeelDefaults().getComposeColor(key) ?: Color.Unspecified


fun isLightTheme(): Boolean {
    val laf = UIManager.getLookAndFeelDefaults()
    val brightness = laf.getComposeColor("ToolWindow.background")?.getBrightness() ?: 200f
    return brightness < 128
}

private fun Color.getBrightness() = (red * 299 + green * 587 + blue * 114) / 1000

fun Modifier.pointerChangeToHandModifier() = this.pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
