package com.jetbrains.packagesearch.plugin.ui.bridge

import androidx.compose.ui.graphics.Color
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import java.awt.Desktop
import java.net.URI
import javax.swing.UIDefaults
import javax.swing.UIManager
import org.jetbrains.jewel.foundation.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.tree.buildTree

fun java.awt.Color.toComposeColor(): Color {
    return Color(red, green, blue, alpha)
}

fun UIDefaults.getComposeColor(key: String): Color? {
    return getColor(key)?.toComposeColor()
}

fun UIDefaults.getComposeColorOrUnspecified(key: String): Color {
    return getColor(key)?.toComposeColor().let {
        println("No color in LAF for $key, fallback to Color.Unspecified")
        Color.Unspecified
    }
}


fun List<PackageSearchModule>.asTree() =
    buildTree {
        groupBy { it.identity.group }
            .values
            .forEach {
                val sortedItems = it.sortedBy { it.identity.path }
                sortedItems.filter { it.identity.path == ":" }
                    .forEach { addNodes(sortedItems, it) }
            }
    }

fun TreeGeneratorScope<PackageSearchModule>.addNodes(
    sortedItems: List<PackageSearchModule>,
    currentData: PackageSearchModule
) {
    val children = sortedItems
        .filter { it.identity.path.startsWith(currentData.identity.path) }
    if (children.isNotEmpty()) addNode(currentData, id = currentData.identity) {
        children.forEach { addNodes(sortedItems.subList(sortedItems.indexOf(currentData) + 1, sortedItems.size), it) }
    }
    else addLeaf(currentData, id = currentData.identity)
}

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